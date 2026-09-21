package com.techeer.carpool.global.jwt;

import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createAccessToken(Long memberId) {
        return buildToken(memberId, accessTokenExpiration, "access");
    }

    public String createRefreshToken(Long memberId) {
        return buildToken(memberId, refreshTokenExpiration, "refresh");
    }

    public boolean validateAccessToken(String token) {
        try {
            getClaims(token, "access");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getMemberIdFromRefreshToken(String token) {
        return memberId(getClaims(token, "refresh"));
    }

    public Long getMemberIdFromToken(String token) {
        return memberId(getClaims(token, "access"));
    }

    private Long memberId(Claims claims) {
        Object value = claims.get("memberId");
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Number) return ((Number) value).longValue();
        throw new MalformedJwtException("JWT memberId is missing or invalid");
    }

    public LocalDateTime getRefreshTokenExpiresAt() {
        return LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration / 1000;
    }

    // 로그아웃 시 AccessToken 블랙리스트 TTL 계산용
    public long getRemainingSeconds(String token) {
        Date expiration = getClaims(token, "access").getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    // 만료(AUTH_005) vs 위변조(AUTH_004) 구분 — TokenReissueService에서 사용
    public void validateRefreshToken(String token) {
        try {
            getClaims(token, "refresh");
        } catch (ExpiredJwtException e) {
            throw new CarpoolException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CarpoolException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String buildToken(Long memberId, long expiration, String tokenUse) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claim("memberId", memberId)
                .claim("token_use", tokenUse)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    private Claims getClaims(String token, String tokenUse) {
        return Jwts.parser()
                .require("token_use", tokenUse)
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
