package com.techeer.carpool.domain.member;

import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.application.service.ApplicationCreateService;
import com.techeer.carpool.domain.application.service.ApplicationStatusService;
import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.member.service.MemberWithdrawService;
import com.techeer.carpool.domain.post.dto.PostCreateRequest;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostType;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.post.service.PostService;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getenv().getOrDefault("MOA_POSTGIS_IMAGE", "postgis/postgis:15-3.5")).asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @Autowired ApplicationCreateService create;
    @Autowired ApplicationStatusService decisions;
    @Autowired MemberWithdrawService withdrawal;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired PostService postService;
    @Autowired ApplicationRepository applications;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean(name = "listenerContainer") RedisMessageListenerContainer listener;
    @MockitoBean RedisCacheManager cache;
    @MockitoBean RefreshTokenRedisRepository refreshTokens;
    @MockitoBean BlacklistRedisRepository blacklist;
    long host, applicant, postId, applicationId;

    @BeforeEach void fixture() {
        when(cache.getCache(anyString())).thenReturn(new org.springframework.cache.concurrent.ConcurrentMapCache("upcoming-posts"));
        host = member(); applicant = member();
        postId = post(host, LocalDateTime.now().plusHours(2)).getId();
        applicationId = create.apply(postId, applicant).getId();
    }
    long member() { return members.save(Member.builder().email(UUID.randomUUID()+"@test.com")
            .password("pw").nickname("참가자").build()).getId(); }
    Post post(long owner, LocalDateTime departure) {
        return posts.save(Post.builder().memberId(owner).title("모아 모집").type(PostType.TAXI)
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(departure).maxPassengers(2).build());
    }

    @Test void expiredEmptyRecruitmentDoesNotPreventWithdrawalAndHistoryRemains() {
        long formerHost = member();
        long expired = post(formerHost, LocalDateTime.now().minusHours(2)).getId();
        withdrawal.withdraw(formerHost, null);
        assertThat(members.findById(formerHost).orElseThrow().isDeleted()).isTrue();
        assertThat(posts.findById(expired).orElseThrow().isDeleted()).isFalse();
        assertThat(posts.findById(expired).orElseThrow().getCurrentPassengers()).isZero();
    }

    @Test void pendingWithdrawalCancelsRequestAndDeletedMemberCannotRejoinOrCreate() throws Exception {
        withdrawal.withdraw(applicant, null);
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertError(() -> decisions.accept(applicationId, host), ErrorCode.MEMBER_NOT_FOUND);
        assertError(() -> create.apply(postId, applicant), ErrorCode.MEMBER_NOT_FOUND);
        PostCreateRequest request = json.readValue("""
                {"type":"TAXI","title":"택시 모집","departureLocation":"강남역","departureLat":37.5,"departureLng":127.0,
                 "destinationLocation":"판교역","destinationLat":37.4,"destinationLng":127.1,"maxPassengers":2,"departureTime":"%s"}
                """.formatted(LocalDateTime.now().plusHours(2)), PostCreateRequest.class);
        assertError(() -> postService.createPost(request, applicant), ErrorCode.MEMBER_NOT_FOUND);
        assertThat(posts.findById(postId).orElseThrow().getCurrentPassengers()).isZero();
    }

    @Test void withdrawalWinningMemberLockPreventsConcurrentApproval() throws Exception {
        raceWithHeldTransaction(() -> withdrawal.withdraw(applicant, null),
                () -> assertError(() -> decisions.accept(applicationId, host), ErrorCode.MEMBER_NOT_FOUND));
        assertThat(members.findById(applicant).orElseThrow().isDeleted()).isTrue();
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(posts.findById(postId).orElseThrow().getCurrentPassengers()).isZero();
    }

    @Test void approvalWinningMemberLockPreventsConcurrentWithdrawal() throws Exception {
        raceWithHeldTransaction(() -> decisions.accept(applicationId, host),
                () -> assertError(() -> withdrawal.withdraw(applicant, null), ErrorCode.MEMBER_ACTIVE_RECRUITMENT));
        assertThat(members.findById(applicant).orElseThrow().isDeleted()).isFalse();
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(posts.findById(postId).orElseThrow().getCurrentPassengers()).isEqualTo(1);
    }

    @Test void futureOwnedRecruitmentStillBlocksWithdrawal() {
        assertError(() -> withdrawal.withdraw(host, null), ErrorCode.MEMBER_ACTIVE_RECRUITMENT);
        assertThat(members.findById(host).orElseThrow().isDeleted()).isFalse();
    }

    @Test void withdrawnRejectedMemberCannotBeRestoredToPending() {
        decisions.reject(applicationId,host);
        withdrawal.withdraw(applicant,null);
        assertError(() -> decisions.cancelReject(applicationId,host),ErrorCode.MEMBER_NOT_FOUND);
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(posts.findById(postId).orElseThrow().getCurrentPassengers()).isZero();
    }

    @Test void withdrawalWinningMemberLockPreventsConcurrentRejectionUndo() throws Exception {
        decisions.reject(applicationId,host);
        raceWithHeldTransaction(() -> withdrawal.withdraw(applicant,null),
                () -> assertError(() -> decisions.cancelReject(applicationId,host),ErrorCode.MEMBER_NOT_FOUND));
        assertThat(members.findById(applicant).orElseThrow().isDeleted()).isTrue();
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test void rejectionUndoWinningMemberLockLetsWithdrawalCancelRestoredRequest() throws Exception {
        decisions.reject(applicationId,host);
        raceWithHeldTransaction(() -> decisions.cancelReject(applicationId,host),
                () -> withdrawal.withdraw(applicant,null));
        assertThat(members.findById(applicant).orElseThrow().isDeleted()).isTrue();
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(posts.findById(postId).orElseThrow().getCurrentPassengers()).isZero();
    }

    private void raceWithHeldTransaction(Runnable winner, Runnable blocked) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<?> first = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            winner.run();
            held.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lock test timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }));
        try {
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = pool.submit(blocked);
            // Observe a real PostgreSQL waiter rather than inferring contention from request duration.
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
                    AND wait_event_type='Lock' AND query LIKE '%members%'
                    """, Long.class)).isGreaterThan(0));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
    private void assertError(Runnable action, ErrorCode error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CarpoolException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(error));
    }
}
