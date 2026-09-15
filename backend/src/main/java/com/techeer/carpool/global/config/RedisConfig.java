package com.techeer.carpool.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.notification.subscriber.RedisNotificationSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // StringRedisTemplate은 Spring Boot 자동 구성 사용 (별도 빈 불필요)

    // Boot가 ObjectMapper를 자동 구성하지 않는 환경(테스트 등)을 위한 폴백
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // 알림 페이로드 직렬화용 (JSON) — Boot가 자동 구성한 ObjectMapper 주입
    @Bean
    public RedisTemplate<String, Object> notificationRedisTemplate(
            RedisConnectionFactory factory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        return template;
    }

    // "notification:*" 패턴 구독 — 메시지 수신 시 RedisNotificationSubscriber.onMessage() 호출
    @Bean
    public RedisMessageListenerContainer listenerContainer(
            RedisConnectionFactory factory,
            RedisNotificationSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(
                (message, pattern) -> subscriber.onMessage(new String(message.getBody(), StandardCharsets.UTF_8),
                        new String(message.getChannel(), StandardCharsets.UTF_8)),
                new PatternTopic("notification:*")
        );
        return container;
    }
}
