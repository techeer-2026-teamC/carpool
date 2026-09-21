package com.techeer.carpool.global.jwt;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.member.repository.MemberRepository;
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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtRedisFailureTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JwtTokenProvider tokens = spy(new JwtTokenProvider("test-signature-secret-at-least-32-characters", 60_000, 120_000));
    private final MemberRepository members = mock(MemberRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, new BlacklistRedisRepository(redis), members);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @BeforeEach
    void setUp() {
        when(members.existsByIdAndDeletedFalse(7L)).thenReturn(true);
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @MethodSource("blacklistFailures")
    void unavailableBlacklistRejectsRequestBeforeAuthentication(DataAccessException error) throws Exception {
        String token = tokens.createAccessToken(7L);
        when(redis.hasKey("blacklist:" + token)).thenThrow(error);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(99L, null, List.of()));

        filter.doFilter(request(token), response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(members);
        verify(tokens, never()).getMemberIdFromToken(anyString());
    }

    @Test
    void memberLookupFailureRejectsRequestAndClearsAuthentication() throws Exception {
        when(members.existsByIdAndDeletedFalse(7L)).thenThrow(new QueryTimeoutException("unavailable"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(99L, null, List.of()));
        filter.doFilter(request(tokens.createAccessToken(7L)), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    static Stream<DataAccessException> blacklistFailures() {
        return Stream.of(new RedisConnectionFailureException("offline"), new QueryTimeoutException("timeout"),
                new RedisSystemException("command unavailable", new IllegalStateException()));
    }

    @Test
    void validAccessTokenUsesSignedMemberIdWithOneParseAndOnlyTheMandatoryRedisCheck() throws Exception {
        String token = tokens.createAccessToken(7L);
        clearInvocations(tokens);
        var request = request(token);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        verify(redis).hasKey("blacklist:" + token);
        verify(tokens).getMemberIdFromToken(token);
        verify(members).existsByIdAndDeletedFalse(7L);
        verifyNoMoreInteractions(tokens, redis);
    }

    @Test
    void invalidSignatureCannotAuthenticateOrQueryMembers() throws Exception {
        String forged = new JwtTokenProvider("different-signature-secret-at-least-32-characters", 60_000, 120_000).createAccessToken(7L);
        var request = request(forged);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("tokenError")).isEqualTo("AUTH_004");
        verifyNoInteractions(members);
    }

    @Test
    void revokedTokenCannotAuthenticateEvenWhenItsSignatureIsValid() throws Exception {
        String token = tokens.createAccessToken(7L);
        when(redis.hasKey("blacklist:" + token)).thenReturn(true);

        filter.doFilter(request(token), response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(members);
        verify(tokens, never()).getMemberIdFromToken(anyString());
    }

    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("GET", "/api/v1/posts/mine");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
