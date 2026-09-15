package com.techeer.carpool.domain.notification.publisher;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisNotificationPublisher {
    private final StringRedisTemplate stringRedisTemplate;

    public void publishSerialized(Long userId, String payload) {
        stringRedisTemplate.convertAndSend("notification:" + userId, payload);
    }
}
