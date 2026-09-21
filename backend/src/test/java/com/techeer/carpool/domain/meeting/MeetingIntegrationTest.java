package com.techeer.carpool.domain.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.application.service.ApplicationStatusService;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.repository.NotificationRepository;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostType;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringJUnitConfig(MeetingIntegrationTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create", "spring.flyway.enabled=false"})
@Sql(statements = """
        CREATE TABLE IF NOT EXISTS meeting_attendance (
            post_id bigint NOT NULL REFERENCES posts(id), member_id bigint NOT NULL REFERENCES members(id),
            status varchar(20) NOT NULL CHECK (status IN ('MET','NO_SHOW')), updated_at timestamp NOT NULL,
            PRIMARY KEY (post_id,member_id));
        """)
class MeetingIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void connections(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", postgres::getJdbcUrl);
        props.add("spring.datasource.username", postgres::getUsername);
        props.add("spring.datasource.password", postgres::getPassword);
        props.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        props.add("spring.data.redis.host", redis::getHost);
        props.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Post.class, Member.class, Application.class, Notification.class})
    @EnableJpaRepositories(basePackageClasses = {PostRepository.class, MemberRepository.class,
            ApplicationRepository.class, NotificationRepository.class})
    @Import({MeetingService.class, MeetingLocations.class, MeetingLocationStore.class, ApplicationStatusService.class, CarpoolMetrics.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MeterRegistry meters() { return new SimpleMeterRegistry(); }
    }
    @Autowired MeetingService meetings;
    @Autowired MeetingLocations locations;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean MeetingLocationStore store;
    @org.springframework.test.context.bean.override.mockito.MockitoBean NotificationService notifications;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher publisher;
    @Autowired ApplicationStatusService applicationStatus;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired ApplicationRepository applications;
    @Autowired PlatformTransactionManager transactions;
    @Autowired StringRedisTemplate strings;
    @Autowired JdbcTemplate jdbc;
    long host, first, second, outsider, postId, firstApplication;
    java.util.Map<Long,String> generations = new java.util.HashMap<>();

    @BeforeEach void participants() {
        host = member(); first = member(); second = member(); outsider = member();
        postId = posts.save(Post.builder().memberId(host).title("택시 만남").type(PostType.TAXI)
                .departureLocation("강남역").destinationLocation("판교역")
                .departureTime(LocalDateTime.now().plusMinutes(10)).maxPassengers(4).build()).getId();
        firstApplication = acceptFixture(first);
        acceptFixture(second);
    }
    long member() { return members.save(Member.builder().email(UUID.randomUUID()+"@test.com")
            .password("pw").nickname("참가자").build()).getId(); }
    long acceptFixture(long member) {
        return new TransactionTemplate(transactions).execute(tx -> {
            Post post = posts.findByIdAndDeletedFalseWithLock(postId).orElseThrow();
            Application application = Application.builder().postId(postId).applicantId(member).build();
            application.accept(); post.incrementPassengers();
            return applications.save(application).getId();
        });
    }

    @Test void onlyParticipantsCanReadAndOnlyHostCanMarkAttendance() {
        assertError(() -> meetings.get(postId, outsider), ErrorCode.MEETING_FORBIDDEN);
        assertError(() -> meetings.mark(postId, first, "MET", second), ErrorCode.MEETING_FORBIDDEN);
        assertError(() -> meetings.mark(postId, first, "NO_SHOW", host), ErrorCode.MEETING_INVALID_STATUS);
        MeetingView view = meetings.mark(postId, first, "MET", host);
        assertThat(view.participants()).filteredOn(p -> p.memberId().equals(first))
                .extracting(MeetingView.Participant::status).containsExactly("MET");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_attendance WHERE post_id=?", Long.class, postId)).isEqualTo(1);
    }

    @Test void earlyCompletionRequiresEveryParticipantResolvedAndClosesCancellation() {
        meetings.mark(postId, first, "MET", host);
        assertError(() -> meetings.complete(postId, host), ErrorCode.MEETING_INVALID_STATUS);
        meetings.mark(postId, second, "MET", host);
        MeetingView completed = meetings.complete(postId, host);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.locationSharingAvailable()).isFalse();
        assertThat(meetings.complete(postId, host).completedAt()).isEqualTo(completed.completedAt());
        assertError(() -> applicationStatus.cancel(firstApplication, first), ErrorCode.RECRUITMENT_CUTOFF);
        assertThat(applications.findById(firstApplication).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    @Test void noShowBecomesAvailableAfterDeparture() {
        jdbc.update("UPDATE posts SET departure_time=? WHERE id=?", LocalDateTime.now().minusMinutes(1), postId);
        meetings.mark(postId, first, "MET", host);
        meetings.mark(postId, second, "NO_SHOW", host);
        MeetingView completed = meetings.complete(postId, host);
        assertThat(completed.participants()).filteredOn(p -> p.memberId().equals(second))
                .extracting(MeetingView.Participant::status).containsExactly("NO_SHOW");
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test void coordinatesAreExplicitEphemeralAndLimitedToHostAndSelf() {
        assertThat(locations.get(postId, host)).isEmpty();
        assertError(() -> locations.update(postId, outsider, 37.5, 127.0, UUID.randomUUID().toString()), ErrorCode.MEETING_FORBIDDEN);
        share(host, 37.5, 127.0);
        share(first, 37.51, 127.01);
        share(second, 37.52, 127.02);
        assertThat(locations.get(postId, host)).extracting(MeetingLocations.Position::memberId)
                .containsExactlyInAnyOrder(host, first, second);
        assertThat(locations.get(postId, first)).extracting(MeetingLocations.Position::memberId)
                .containsExactlyInAnyOrder(host, first);
        String firstKey = "moa:meeting:"+postId+":member:"+first;
        assertThat(strings.getExpire(firstKey, TimeUnit.SECONDS)).isBetween(55L,60L);
        locations.stop(postId, first, generations.get(first));
        assertThat(locations.get(postId, first)).extracting(MeetingLocations.Position::memberId).containsExactly(host);
        String secondKey = "moa:meeting:"+postId+":member:"+second;
        strings.expire(secondKey, Duration.ofMillis(20));
        await().atMost(2,TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(locations.get(postId, host)).extracting(MeetingLocations.Position::memberId).containsExactly(host));
    }

    @Test void cancelledParticipantLosesLocationAccessAndAttendance() {
        meetings.mark(postId, first, "MET", host);
        share(first, 37.5, 127.0);
        applicationStatus.cancel(firstApplication, first);
        assertError(() -> meetings.get(postId, first), ErrorCode.MEETING_FORBIDDEN);
        assertError(() -> locations.get(postId, first), ErrorCode.MEETING_FORBIDDEN);
        assertThat(locations.get(postId, host)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_attendance WHERE post_id=? AND member_id=?",
                Long.class,postId,first)).isZero();
    }

    @Test void delayedPositionIsNotRepublishedAfterStopReplacementOrExpiry() throws Exception {
        var json = new ObjectMapper();
        var messages = org.mockito.Mockito.mock(org.springframework.messaging.simp.SimpMessagingTemplate.class);
        var fanout = new MeetingSocket.Fanout(json, meetings, messages, locations);
        share(first, 37.5, 127.0);
        var position = locations.get(postId, first).get(0);
        byte[] payload = json.writeValueAsBytes(position);
        var message = new org.springframework.data.redis.connection.DefaultMessage(MeetingLocations.CHANNEL.getBytes(), payload);
        fanout.onMessage(message, null);
        org.mockito.Mockito.verify(messages).convertAndSend("/topic/meetings/"+postId+"/members/"+host, position);
        org.mockito.Mockito.clearInvocations(messages);
        locations.stop(postId, first, generations.get(first));
        fanout.onMessage(message, null);
        String key = "moa:meeting:"+postId+":member:"+first;
        var newer = new MeetingLocations.Position(postId, first, 37.51, 127.01, java.time.Instant.now().toString());
        strings.opsForValue().set(key, json.writeValueAsString(newer), Duration.ofSeconds(60));
        fanout.onMessage(message, null);
        strings.opsForValue().set(key, new String(payload, java.nio.charset.StandardCharsets.UTF_8), Duration.ofMillis(20));
        await().atMost(2,TimeUnit.SECONDS).until(() -> !Boolean.TRUE.equals(strings.hasKey(key)));
        fanout.onMessage(message, null);
        org.mockito.Mockito.verifyNoInteractions(messages);
    }

    String share(long member, double latitude, double longitude) {
        String generation = locations.start(postId, member).generation();
        generations.put(member, generation);
        locations.update(postId, member, latitude, longitude, generation);
        return generation;
    }

    @Test void stoppedGenerationCannotWriteAfterAnAlreadyAuthorizedUpdateResumes() throws Exception {
        String generation = locations.start(postId, first).generation();
        var authorized = new java.util.concurrent.CountDownLatch(1);
        var resume = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            authorized.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(store).update(org.mockito.ArgumentMatchers.eq(postId), org.mockito.ArgumentMatchers.eq(first),
                org.mockito.ArgumentMatchers.eq(generation), org.mockito.ArgumentMatchers.anyString());
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var update = executor.submit(() -> locations.update(postId, first, 37.5, 127.0, generation));
            assertThat(authorized.await(10, TimeUnit.SECONDS)).isTrue();
            locations.stop(postId, first, generation);
            resume.countDown();
            update.get(10, TimeUnit.SECONDS);
            assertThat(locations.get(postId, host)).isEmpty();
            assertThat(strings.hasKey("moa:meeting:"+postId+":member:"+first+":rate")).isFalse();
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @Test void oldStopsAndUpdatesCannotAlterRestartedSharing() {
        String old = share(first, 37.5, 127.0);
        String current = share(first, 37.6, 127.1);
        assertThat(current).isNotEqualTo(old);
        locations.stop(postId, first, old);
        locations.update(postId, first, 38.0, 128.0, old);
        assertThat(locations.get(postId, host)).singleElement()
                .extracting(MeetingLocations.Position::latitude).isEqualTo(37.6);
        locations.stop(postId, first, current);
        assertThat(locations.get(postId, host)).isEmpty();
    }

    @Test void cancellationAndReacceptanceRequireFreshSharingConsent() {
        String old = share(first, 37.5, 127.0);
        applicationStatus.cancelAccept(firstApplication, host);
        assertThat(store.get(postId, first)).isNull();
        applicationStatus.accept(firstApplication, host);
        locations.update(postId, first, 37.6, 127.1, old);
        assertThat(locations.get(postId, host)).isEmpty();
        share(first, 37.7, 127.2);
        assertThat(locations.get(postId, host)).hasSize(1);
    }

    @Test void startCannotRecreateSharingBetweenRevocationAndCancellationCommit() throws Exception {
        share(first, 37.5, 127.0);
        var revoked = new java.util.concurrent.CountDownLatch(1);
        var commit = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            Object result = call.callRealMethod();
            revoked.countDown();
            assertThat(commit.await(10, TimeUnit.SECONDS)).isTrue();
            return result;
        }).when(store).revoke(postId, first);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var cancellation = executor.submit(() -> applicationStatus.cancelAccept(firstApplication, host));
            assertThat(revoked.await(10, TimeUnit.SECONDS)).isTrue();
            var start = executor.submit(() -> locations.start(postId, first));
            await().atMost(5, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Long.class) > 0);
            commit.countDown();
            cancellation.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> start.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(CarpoolException.class);
            assertThat(strings.hasKey("moa:meeting:"+postId+":member:"+first+":session")).isFalse();
        } finally { commit.countDown(); executor.shutdownNow(); }
    }

    @Test void failedRevocationRollsBackMembershipCancellation() {
        share(first, 37.5, 127.0);
        org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("unavailable"))
                .when(store).revoke(postId, first);
        assertThatThrownBy(() -> applicationStatus.cancelAccept(firstApplication, host))
                .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
        assertThat(applications.findById(firstApplication).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(meetings.get(postId, first).participants()).hasSize(3);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CarpoolException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
