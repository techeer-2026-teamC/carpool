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
    @Import({MeetingService.class, ApplicationStatusService.class, CarpoolMetrics.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MeterRegistry meters() { return new SimpleMeterRegistry(); }
    }
    @Autowired MeetingService meetings;
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

    @Test void cancelledParticipantLosesAccessAndAttendance() {
        meetings.mark(postId, first, "MET", host);
        applicationStatus.cancel(firstApplication, first);
        assertError(() -> meetings.get(postId, first), ErrorCode.MEETING_FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_attendance WHERE post_id=? AND member_id=?",
                Long.class,postId,first)).isZero();
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CarpoolException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
