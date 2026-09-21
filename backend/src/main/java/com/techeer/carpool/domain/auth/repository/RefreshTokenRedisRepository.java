package com.techeer.carpool.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

    private static final String KEY_PREFIX = "refresh:member:";
    private static final long TTL_SECONDS = 604800L; // 7일
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void save(Long memberId, String token) {
        redisTemplate.opsForValue().set(KEY_PREFIX + memberId, token, TTL_SECONDS, TimeUnit.SECONDS);
    }

    public Optional<String> findByMemberId(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + memberId));
    }

    public boolean rotate(Long memberId, String expectedToken, String replacement) {
        return Long.valueOf(1).equals(redisTemplate.execute(ROTATE, List.of(KEY_PREFIX + memberId),
                expectedToken, replacement, Long.toString(TTL_SECONDS)));
    }

    public void delete(Long memberId) {
        redisTemplate.delete(KEY_PREFIX + memberId);
    }
}
