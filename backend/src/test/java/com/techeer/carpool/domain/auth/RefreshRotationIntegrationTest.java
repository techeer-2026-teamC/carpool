package com.techeer.carpool.domain.auth;

import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.auth.service.TokenReissueService;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class RefreshRotationIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    final JwtTokenProvider tokens = spy(new JwtTokenProvider("rotation-regression-secret-at-least-32-bytes", 60_000, 120_000));
    LettuceConnectionFactory connection;
    RefreshTokenRedisRepository repository;
    TokenReissueService service;

    @BeforeEach void connect() {
        connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        repository = new RefreshTokenRedisRepository(new StringRedisTemplate(connection));
        service = new TokenReissueService(repository, tokens);
    }
    @AfterEach void close() { connection.destroy(); }

    @Test void concurrentUseOfOneTokenHasExactlyOneWinner() throws Exception {
        String old = tokens.createRefreshToken(7L);
        repository.save(7L, old);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> attempt = () -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            try { service.reissue(old); return true; }
            catch (CarpoolException failure) {
                assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
                return false;
            }
        };
        try {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(repository.findByMemberId(7L)).hasValueSatisfying(value -> assertThat(value).isNotEqualTo(old));
        } finally { pool.shutdownNow(); }
    }

    @Test void logoutDuringReissueCannotRestoreDeletedSession() throws Exception {
        String old = tokens.createRefreshToken(8L);
        repository.save(8L, old);
        var prepared = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        doAnswer(call -> {
            prepared.countDown();
            assertThat(resume.await(5, TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(tokens).createAccessToken(8L);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var pending = pool.submit(() -> assertThatThrownBy(() -> service.reissue(old))
                    .isInstanceOfSatisfying(CarpoolException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN)));
            assertThat(prepared.await(5, TimeUnit.SECONDS)).isTrue();
            repository.delete(8L);
            resume.countDown();
            pending.get(5, TimeUnit.SECONDS);
            assertThat(repository.findByMemberId(8L)).isEmpty();
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test void staleTokenCannotOverwriteNewLogin() {
        String old = tokens.createRefreshToken(9L);
        String newer = tokens.createRefreshToken(9L);
        repository.save(9L, newer);
        assertThatThrownBy(() -> service.reissue(old)).isInstanceOf(CarpoolException.class);
        assertThat(repository.findByMemberId(9L)).contains(newer);
        assertThat(service.reissue(newer).refreshToken()).isNotEqualTo(newer);
    }
}
