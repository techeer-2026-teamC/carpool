package com.techeer.carpool.domain.auth;

import com.techeer.carpool.domain.auth.dto.AuthTokens;
import com.techeer.carpool.domain.auth.dto.LoginRequest;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.auth.service.MemberLoginService;
import com.techeer.carpool.domain.auth.service.TokenReissueService;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.member.service.MemberWithdrawService;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.repository.NotificationRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberTokenRevocationIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getenv().getOrDefault("MOA_POSTGIS_IMAGE", "postgis/postgis:15-3.5"))
            .asCompatibleSubstituteFor("postgres"));
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired MemberLoginService login;
    @Autowired MemberWithdrawService withdrawal;
    @Autowired TokenReissueService reissue;
    @Autowired RefreshTokenRedisRepository refresh;
    @Autowired JwtTokenProvider tokens;
    @Autowired NotificationRepository notifications;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void withdrawalRejectsEveryAccessTokenAndNewSubscriptionWithoutWritingNotification() throws Exception {
        Member member = member();
        AuthTokens first = login.login(request(member)), second = login.login(request(member));
        for (String token : java.util.List.of(first.accessToken(), second.accessToken())) {
            mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        }
        long notificationId = notifications.save(Notification.ofApplicationReceived(member.getId(), 100L)).getNotificationId();
        mvc.perform(delete("/api/v1/members/me").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk());
        for (String token : java.util.List.of(first.accessToken(), second.accessToken())) {
            mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/notifications/subscribe").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(notifications.findById(notificationId).orElseThrow().getReadAt()).isNull();
        assertThat(members.findById(member.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(refresh.findByMemberId(member.getId())).isEmpty();
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", second.refreshToken())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test void withdrawalWinningMemberLockPreventsRefresh() throws Exception {
        Member member = member();
        AuthTokens original = login.login(request(member));
        raceWithHeldTransaction(() -> withdrawal.withdraw(member.getId(), original.accessToken()),
                () -> assertError(() -> reissue.reissue(original.refreshToken()), ErrorCode.INVALID_TOKEN));
        assertThat(refresh.findByMemberId(member.getId())).isEmpty();
    }

    @Test void refreshWinningMemberLockCannotOutliveWithdrawal() throws Exception {
        Member member = member();
        AuthTokens original = login.login(request(member));
        AtomicReference<AuthTokens> renewed = new AtomicReference<>();
        raceWithHeldTransaction(() -> renewed.set(reissue.reissue(original.refreshToken())),
                () -> withdrawal.withdraw(member.getId(), original.accessToken()));
        assertRevoked(member.getId(), renewed.get());
    }

    @Test void withdrawalWinningMemberLockPreventsLogin() throws Exception {
        Member member = member();
        LoginRequest request = request(member);
        raceWithHeldTransaction(() -> withdrawal.withdraw(member.getId(), null),
                () -> assertError(() -> login.login(request), ErrorCode.MEMBER_NOT_FOUND));
        assertThat(refresh.findByMemberId(member.getId())).isEmpty();
    }

    @Test void loginWinningMemberLockCannotRestoreSessionAfterWithdrawal() throws Exception {
        Member member = member();
        LoginRequest request = request(member);
        AtomicReference<AuthTokens> issued = new AtomicReference<>();
        raceWithHeldTransaction(() -> issued.set(login.login(request)),
                () -> withdrawal.withdraw(member.getId(), null));
        assertRevoked(member.getId(), issued.get());
    }

    @Test void deletedAndMissingMembersCannotRefreshEvenWithMatchingRedisTokens() throws Exception {
        Member member = member();
        withdrawal.withdraw(member.getId(), null);
        for (long id : new long[]{member.getId(), Long.MAX_VALUE}) {
            String token = tokens.createRefreshToken(id);
            refresh.save(id, token);
            mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", token)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_004"));
            assertThat(refresh.findByMemberId(id)).contains(token);
        }
    }

    @Test void logoutRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        Member member = member();
        String token = tokens.createAccessToken(member.getId());
        withdrawal.withdraw(member.getId(), null);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private Member member() {
        return members.save(Member.builder().email(UUID.randomUUID() + "@test.com")
                .password(passwords.encode("password123")).nickname("참가자").build());
    }

    private LoginRequest request(Member member) {
        return json.readValue("{\"email\":\"" + member.getEmail() + "\",\"password\":\"password123\"}", LoginRequest.class);
    }

    private void assertRevoked(long memberId, AuthTokens issued) throws Exception {
        assertThat(members.findById(memberId).orElseThrow().isDeleted()).isTrue();
        assertThat(refresh.findByMemberId(memberId)).isEmpty();
        assertError(() -> reissue.reissue(issued.refreshToken()), ErrorCode.INVALID_TOKEN);
        mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + issued.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    private void raceWithHeldTransaction(Runnable winner, Runnable blocked) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<?> first = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            winner.run();
            held.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lock test timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }));
        try {
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = pool.submit(blocked);
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
