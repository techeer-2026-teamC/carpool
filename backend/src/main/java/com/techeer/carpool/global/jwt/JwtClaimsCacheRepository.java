package com.techeer.carpool.global.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Access Token → memberId 매핑을 Redis에 캐싱.
 * 매 요청마다 발생하는 HMAC 서명 검증(CPU 연산)을 캐시 히트 시 스킵.
 * Key는 SHA-256 해시를 사용해 토큰 원문이 Redis에 저장되지 않도록 함.
 */
@Repository
@RequiredArgsConstructor
public class JwtClaimsCacheRepository {

    private static final String PREFIX = "jwt:claims:";
    private final StringRedisTemplate redisTemplate;

    public void save(String token, Long memberId, long ttlSeconds) {
        if (ttlSeconds <= 0) return;
        try {
            redisTemplate.opsForValue().set(PREFIX + hash(token), memberId.toString(), ttlSeconds, TimeUnit.SECONDS);
        } catch (org.springframework.dao.DataAccessException ignored) {
            // Optional cache. A miss must fall back to signature verification.
        }
    }

    public Optional<Long> findMemberId(String token) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + hash(token));
            return Optional.ofNullable(value).map(Long::parseLong);
        } catch (org.springframework.dao.DataAccessException | NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public void delete(String token) {
        try {
            redisTemplate.delete(PREFIX + hash(token));
        } catch (org.springframework.dao.DataAccessException ignored) {
            // Revocation is enforced independently by the mandatory blacklist check.
        }
    }

    private String hash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
