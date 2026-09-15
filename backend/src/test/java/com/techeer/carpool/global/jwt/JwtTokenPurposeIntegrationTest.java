package com.techeer.carpool.global.jwt;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.auth.service.TokenReissueService;
import com.techeer.carpool.domain.meeting.MeetingService;
import com.techeer.carpool.global.config.WebSocketAuthChannelInterceptor;
import com.techeer.carpool.global.exception.CarpoolException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Testcontainers
class JwtTokenPurposeIntegrationTest {
    static final String SECRET = "purpose-regression-test-secret-at-least-32-bytes";
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    final JwtTokenProvider tokens = new JwtTokenProvider(SECRET, 60_000, 120_000);
    LettuceConnectionFactory connection;
    JwtClaimsCacheRepository claims;
    RefreshTokenRedisRepository refresh;
    TokenReissueService reissue;
    JwtAuthenticationFilter filter;
    WebSocketAuthChannelInterceptor sockets;

    @BeforeEach void configure() {
        connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        var strings = new StringRedisTemplate(connection);
        claims = new JwtClaimsCacheRepository(strings);
        refresh = new RefreshTokenRedisRepository(strings);
        reissue = new TokenReissueService(refresh, tokens);
        var blacklist = new BlacklistRedisRepository(strings);
        filter = new JwtAuthenticationFilter(tokens, blacklist, claims);
        sockets = new WebSocketAuthChannelInterceptor(tokens, blacklist, mock(MeetingService.class));
    }

    @AfterEach void close() {
        SecurityContextHolder.clearContext();
        connection.destroy();
    }

    @Test void accessAuthenticatesHttpAndSocketWhileRefreshOnlyReissues() throws Exception {
        String access = tokens.createAccessToken(7L);
        http(access);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        assertThat(connect(access).getUser().getName()).isEqualTo("7");
        String refreshToken = tokens.createRefreshToken(7L);
        refresh.save(7L, refreshToken);
        var renewed = reissue.reissue(refreshToken);
        assertThat(renewed.refreshToken()).isNotEqualTo(refreshToken);
        assertThat(tokens.validateAccessToken(renewed.accessToken())).isTrue();
        assertThat(tokens.validateAccessToken(renewed.refreshToken())).isFalse();
    }

    @Test void cachedRefreshCannotAuthenticateBeforeOrAfterRotationAndDeletion() throws Exception {
        String old = tokens.createRefreshToken(7L);
        refresh.save(7L, old);
        rejectEvenIfCached(old, "AUTH_004");
        var renewed = reissue.reissue(old);
        assertThat(renewed.refreshToken()).isNotEqualTo(old);
        assertThatThrownBy(() -> reissue.reissue(old)).isInstanceOf(CarpoolException.class);
        rejectEvenIfCached(old, "AUTH_004");
        refresh.delete(7L);
        assertThatThrownBy(() -> reissue.reissue(renewed.refreshToken())).isInstanceOf(CarpoolException.class);
        rejectEvenIfCached(renewed.refreshToken(), "AUTH_004");
    }

    @Test void accessCannotBeUsedForRefreshEvenWhenStoredAsRefresh() {
        String access = tokens.createAccessToken(7L);
        refresh.save(7L, access);
        assertThatThrownBy(() -> reissue.reissue(access)).isInstanceOf(CarpoolException.class);
        assertThat(refresh.findByMemberId(7L)).contains(access);
    }

    @Test void legacyTokenWithoutPurposeRequiresNewLoginEvenWhenCachedOrStored() throws Exception {
        String legacy = Jwts.builder().claim("memberId", 7L).expiration(new Date(System.currentTimeMillis()+60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        rejectEvenIfCached(legacy, "AUTH_004");
        refresh.save(7L, legacy);
        assertThatThrownBy(() -> reissue.reissue(legacy)).isInstanceOf(CarpoolException.class);
    }

    @Test void cachedAccessStillRequiresValidSignatureAndUnexpiredAuthentication() throws Exception {
        String forged = new JwtTokenProvider("different-purpose-regression-signing-secret", 60_000, 120_000)
                .createAccessToken(7L);
        rejectEvenIfCached(forged, "AUTH_004");
        String expired = new JwtTokenProvider(SECRET, -1000, 120_000).createAccessToken(7L);
        rejectEvenIfCached(expired, "AUTH_005");
    }

    private void rejectEvenIfCached(String token, String expectedError) throws Exception {
        claims.save(token, 7L, 60);
        assertThat(claims.findMemberId(token)).contains(7L);
        SecurityContextHolder.clearContext();
        assertThat(http(token).getAttribute("tokenError")).isEqualTo(expectedError);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThatThrownBy(() -> connect(token)).isInstanceOf(MessagingException.class);
    }

    private MockHttpServletRequest http(String token) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/posts/42/meeting/locations");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return request;
    }

    private StompHeaderAccessor connect(String token) {
        var frame = StompHeaderAccessor.create(StompCommand.CONNECT);
        frame.setSessionAttributes(new HashMap<>());
        frame.setNativeHeader("Authorization", "Bearer " + token);
        frame.setLeaveMutable(true);
        sockets.preSend(MessageBuilder.createMessage(new byte[0], frame.getMessageHeaders()), null);
        return frame;
    }
}
