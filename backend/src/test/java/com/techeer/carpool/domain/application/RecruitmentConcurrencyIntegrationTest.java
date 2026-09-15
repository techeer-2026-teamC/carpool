package com.techeer.carpool.domain.application;

import com.techeer.carpool.domain.application.dto.ApplicationResponse;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.application.service.ApplicationCreateService;
import com.techeer.carpool.domain.application.service.ApplicationStatusService;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.entity.*;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.post.service.PostCloseService;
import com.techeer.carpool.domain.notification.repository.NotificationRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "app.legacy-ride.enabled=false"})
@ActiveProfiles("test")
class RecruitmentConcurrencyIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getenv().getOrDefault("MOA_POSTGIS_IMAGE", "postgis/postgis:15-3.5")).asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired ApplicationCreateService createService;
    @Autowired ApplicationStatusService statusService;
    @Autowired ApplicationRepository applications;
    @Autowired PostRepository posts;
    @Autowired MemberRepository members;
    @Autowired NotificationRepository notifications;
    @Autowired PostCloseService closeService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.techeer.carpool.domain.member.service.MemberWithdrawService withdrawal;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean(name = "listenerContainer") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean RedisCacheManager cacheManager;
    long owner;
    long passenger1;
    long passenger2;

    @BeforeEach void createMembers() {
        org.mockito.Mockito.when(cacheManager.getCache(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new org.springframework.cache.concurrent.ConcurrentMapCache("upcoming-posts"));
        owner = member(); passenger1 = member(); passenger2 = member();
    }
    private long member() {
        return members.save(Member.builder().email(UUID.randomUUID() + "@test.com").password("pw").nickname("참여자").build()).getId();
    }
    private Post post(int capacity, PostType type) {
        return posts.save(Post.builder().memberId(owner).title("모아 모집").type(type)
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusHours(2)).maxPassengers(capacity).price(3000).build());
    }

    @Test void concurrentApprovalsOnSeparateTransactionsNeverOverbookLastSeat() throws Exception {
        Post post = post(1, PostType.CARPOOL);
        long first = createService.apply(post.getId(), passenger1).getId();
        long second = createService.apply(post.getId(), passenger2).getId();
        List<Boolean> outcomes = race(() -> approve(first), () -> approve(second));
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(posts.findById(post.getId()).orElseThrow().getCurrentPassengers()).isEqualTo(1);
        assertThat(applications.countByPostIdAndStatus(post.getId(), ApplicationStatus.ACCEPTED)).isEqualTo(1);
    }

    @Test void concurrentDuplicateApprovalChangesCounterOnlyOnce() throws Exception {
        Post post = post(3, PostType.CARPOOL);
        long applicationId = createService.apply(post.getId(), passenger1).getId();
        assertThat(race(() -> approve(applicationId), () -> approve(applicationId))).containsExactlyInAnyOrder(true, false);
        assertThat(posts.findById(post.getId()).orElseThrow().getCurrentPassengers()).isEqualTo(1);
    }

    @Test void rollbackRestoresApplicationCounterAndNotificationTogether() {
        Post post = post(1, PostType.CARPOOL);
        long applicationId = createService.apply(post.getId(), passenger1).getId();
        long notificationCount = notifications.count();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            statusService.accept(applicationId, owner);
            throw new IllegalStateException("simulated commit cancellation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(posts.findById(post.getId()).orElseThrow().getCurrentPassengers()).isZero();
        assertThat(notifications.count()).isEqualTo(notificationCount);
    }

    @Test void selfCancellationReopensFullTaxiButPreservesOwnerClosureAndCanReapply() {
        Post taxi = post(2, PostType.TAXI);
        long app = createService.apply(taxi.getId(), passenger1).getId();
        statusService.accept(app, owner);
        statusService.cancel(app, passenger1);
        assertThat(posts.findById(taxi.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.OPEN);
        assertThat(createService.apply(taxi.getId(), passenger1).getId()).isEqualTo(app);
        statusService.accept(app, owner);
        closeService.closePost(taxi.getId(), owner);
        statusService.cancel(app, passenger1);
        assertThat(posts.findById(taxi.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    @Test void meetingCompletionBlocksApprovalAndCancellationBeforeDeparture() {
        Post post = post(2, PostType.CARPOOL);
        long app = createService.apply(post.getId(), passenger1).getId();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> posts.findByIdAndDeletedFalseWithLock(post.getId())
                .orElseThrow().completeMeeting(LocalDateTime.now()));
        assertThatThrownBy(() -> statusService.accept(app, owner)).isInstanceOfSatisfying(CarpoolException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECRUITMENT_CUTOFF));
        assertThatThrownBy(() -> statusService.cancel(app, passenger1)).isInstanceOf(CarpoolException.class);
    }

    @Test void withdrawalCannotRewriteAcceptedSeatsOutsideRecruitmentRules() {
        Post post = post(1, PostType.CARPOOL);
        long application = createService.apply(post.getId(), passenger1).getId();
        statusService.accept(application, owner);
        assertThatThrownBy(() -> withdrawal.withdraw(passenger1, null))
                .isInstanceOfSatisfying(CarpoolException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACTIVE_RECRUITMENT));
        assertThat(applications.findById(application).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(posts.findById(post.getId()).orElseThrow().getCurrentPassengers()).isEqualTo(1);
        assertThat(members.findById(passenger1).orElseThrow().isDeleted()).isFalse();
    }

    private boolean approve(long id) {
        try { statusService.accept(id, owner); return true; }
        catch (CarpoolException e) {
            assertThat(e.getErrorCode()).isIn(ErrorCode.APPLICATION_POST_FULL, ErrorCode.APPLICATION_ALREADY_PROCESSED);
            return false;
        }
    }
    private List<Boolean> race(Callable<Boolean> a, Callable<Boolean> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> { ready.countDown(); start.await(); return a.call(); });
            Future<Boolean> second = pool.submit(() -> { ready.countDown(); start.await(); return b.call(); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
