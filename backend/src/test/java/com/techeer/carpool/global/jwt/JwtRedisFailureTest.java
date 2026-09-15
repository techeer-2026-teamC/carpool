package com.techeer.carpool.global.jwt;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtRedisFailureTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final JwtTokenProvider tokens = spy(new JwtTokenProvider("test-signature-secret-at-least-32-characters", 60_000, 120_000));
    private final JwtClaimsCacheRepository claims = new JwtClaimsCacheRepository(redis);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, new BlacklistRedisRepository(redis), claims);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @BeforeEach
    void setUp() { when(redis.opsForValue()).thenReturn(values); }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @MethodSource("blacklistFailures")
    void unavailableBlacklistRejectsRequestBeforeUsingCachedClaims(DataAccessException error) throws Exception {
        String token = tokens.createAccessToken(7L);
        when(redis.hasKey("blacklist:" + token)).thenThrow(error);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(99L, null, List.of()));

        filter.doFilter(request(token), response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(values);
        verify(tokens, never()).getMemberIdFromToken(anyString());
    }

    static Stream<DataAccessException> blacklistFailures() {
        return Stream.of(new RedisConnectionFailureException("offline"), new QueryTimeoutException("timeout"),
                new RedisSystemException("command unavailable", new IllegalStateException()));
    }

    @Test
    void optionalClaimsCacheFailureUsesSignatureAndToleratesCacheWriteFailure() throws Exception {
        String token = tokens.createAccessToken(7L);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        doThrow(new RedisConnectionFailureException("offline")).when(values)
                .set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        var request = request(token);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        verify(redis).hasKey("blacklist:" + token);
        verify(tokens).getMemberIdFromToken(token);
    }

    @Test
    void cacheFailureNeverMakesAnInvalidSignatureAuthenticate() throws Exception {
        String forged = new JwtTokenProvider("different-signature-secret-at-least-32-characters", 60_000, 120_000).createAccessToken(7L);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        var request = request(forged);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("tokenError")).isEqualTo("AUTH_004");
        verify(values, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void malformedClaimsCacheValueFallsBackToSignatureVerification() throws Exception {
        String token = tokens.createAccessToken(7L);
        when(values.get(anyString())).thenReturn("broken-value");

        filter.doFilter(request(token), response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        verify(tokens).getMemberIdFromToken(token);
    }

    @Test
    void revokedTokenCannotUsePreviouslyCachedClaims() throws Exception {
        String token = tokens.createAccessToken(7L);
        when(redis.hasKey("blacklist:" + token)).thenReturn(true);

        filter.doFilter(request(token), response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(values);
    }

    @Test
    void expiredCacheTtlIsNotWrittenAndOptionalDeletionCanFail() {
        claims.save("expired-token", 7L, 0);
        verifyNoInteractions(values);
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        assertThatCode(() -> claims.delete("token")).doesNotThrowAnyException();
    }

    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("GET", "/api/v1/posts/mine");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
