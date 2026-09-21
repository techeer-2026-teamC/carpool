package com.techeer.carpool.domain.driver;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.driver.dto.DriverRequest;
import com.techeer.carpool.domain.driver.entity.CarColor;
import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.driver.service.DriverService;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.member.service.MemberWithdrawService;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DriverLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getenv().getOrDefault("MOA_POSTGIS_IMAGE", "postgis/postgis:15-3.5"))
            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired DriverService service;
    @Autowired DriverRepository drivers;
    @Autowired MemberRepository members;
    @Autowired MemberWithdrawService withdrawal;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean(name = "listenerContainer") RedisMessageListenerContainer listener;
    @MockitoBean RefreshTokenRedisRepository refreshTokens;
    @MockitoBean BlacklistRedisRepository blacklist;

    @Test void concurrentRegistrationsKeepOneActiveDriver() throws Exception {
        long memberId = member();
        DriverRequest first = request("11가1001"), second = request("22나1002");
        raceWithHeldTransaction(() -> service.register(memberId, first),
                () -> assertError(() -> service.register(memberId, second), ErrorCode.DRIVER_ALREADY_REGISTERED));
        assertThat(activeCount(memberId)).isEqualTo(1);
        assertThat(service.getMyDriver(memberId).getCarNumber()).isEqualTo("11가1001");
    }

    @Test void withdrawalWinningMemberLockPreventsRegistration() throws Exception {
        long memberId = member();
        DriverRequest request = request("33다1003");
        raceWithHeldTransaction(() -> withdrawal.withdraw(memberId, null),
                () -> assertError(() -> service.register(memberId, request), ErrorCode.MEMBER_NOT_FOUND));
        assertThat(members.findById(memberId).orElseThrow().isDeleted()).isTrue();
        assertThat(activeCount(memberId)).isZero();
    }

    @Test void registrationWinningMemberLockIsDeletedByWithdrawal() throws Exception {
        long memberId = member();
        DriverRequest request = request("44라1004");
        raceWithHeldTransaction(() -> service.register(memberId, request),
                () -> withdrawal.withdraw(memberId, null));
        assertThat(members.findById(memberId).orElseThrow().isDeleted()).isTrue();
        assertThat(activeCount(memberId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM drivers WHERE member_id=? AND deleted=true",
                Long.class, memberId)).isEqualTo(1);
    }

    @Test void databaseConstraintRejectsDuplicateActiveDriverAndPreservesDeletedHistory() throws Exception {
        long memberId = member();
        Driver historical = driver(memberId, "55마1005");
        historical.delete();
        drivers.saveAndFlush(historical);
        service.register(memberId, request("66바1006"));
        assertThatThrownBy(() -> drivers.saveAndFlush(driver(memberId, "77사1007")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_drivers_active_member");
        assertThat(activeCount(memberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM drivers WHERE member_id=?", Long.class, memberId))
                .isEqualTo(2);
    }

    @Test void migrationRejectsExistingDuplicatesWithoutDeletingThemAndCanBeRetried() {
        String schema = "driver_preflight_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            jdbc.execute("CREATE TABLE " + schema + ".drivers (driver_id bigint PRIMARY KEY, member_id bigint NOT NULL, deleted boolean NOT NULL)");
            jdbc.execute("INSERT INTO " + schema + ".drivers VALUES (1, 42, false), (2, 42, false)");
            Flyway flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(schema).defaultSchema(schema).baselineOnMigrate(true).baselineVersion("7").target("8").load();
            assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                    .hasStackTraceContaining("docs/migrations/V8-active-driver.md");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".drivers WHERE deleted=false", Long.class))
                    .isEqualTo(2);
            jdbc.execute("UPDATE " + schema + ".drivers SET deleted=true WHERE driver_id=2");
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.execute("INSERT INTO " + schema + ".drivers VALUES (3, 42, false)"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private long member() {
        return members.save(Member.builder().email(UUID.randomUUID() + "@test.com")
                .password("pw").nickname("운전자").build()).getId();
    }

    private DriverRequest request(String plate) {
        return json.readValue("{\"carModel\":\"소나타\",\"carColor\":\"WHITE\",\"carNumber\":\"" + plate + "\"}", DriverRequest.class);
    }

    private Driver driver(long memberId, String plate) {
        return Driver.builder().memberId(memberId).carModel("소나타").carColor(CarColor.WHITE).carNumber(plate).build();
    }

    private long activeCount(long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM drivers WHERE member_id=? AND deleted=false", Long.class, memberId);
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
