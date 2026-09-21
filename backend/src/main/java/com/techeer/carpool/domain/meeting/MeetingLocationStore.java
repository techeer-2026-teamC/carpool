package com.techeer.carpool.domain.meeting;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MeetingLocationStore {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> START = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
            redis.call('DEL', KEYS[1], KEYS[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> UPDATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            if not redis.call('SET', KEYS[3], '1', 'NX', 'PX', 4000) then return 0 end
            local ttl = math.min(60000, redis.call('PTTL', KEYS[2]))
            if ttl <= 0 then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
            redis.call('PUBLISH', ARGV[3], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> STOP = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            return redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
            """, Long.class);

    public void start(Long postId, Long memberId, String generation, long ttlMillis) {
        redis.execute(START, keys(postId, memberId), generation, String.valueOf(ttlMillis));
    }
    public void update(Long postId, Long memberId, String generation, String position) {
        // Comparison, rate limiting, storage and publication have one Redis ordering point.
        redis.execute(UPDATE, keys(postId, memberId), generation, position, MeetingLocations.CHANNEL);
    }
    public void stop(Long postId, Long memberId, String generation) {
        redis.execute(STOP, keys(postId, memberId), generation);
    }
    public void revoke(Long postId, Long memberId) {
        redis.delete(keys(postId, memberId));
    }
    public String get(Long postId, Long memberId) {
        return redis.opsForValue().get(key(postId, memberId));
    }
    private List<String> keys(Long postId, Long memberId) {
        String key = key(postId, memberId);
        return List.of(key, key + ":session", key + ":rate");
    }
    private String key(Long postId, Long memberId) {
        return "moa:meeting:" + postId + ":member:" + memberId;
    }
}
