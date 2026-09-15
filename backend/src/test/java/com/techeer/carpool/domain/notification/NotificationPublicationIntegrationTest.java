package com.techeer.carpool.domain.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.emitter.SseEmitterRegistry;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.notification.subscriber.RedisNotificationSubscriber;
import com.techeer.carpool.global.config.RedisConfig;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@Testcontainers
class NotificationPublicationIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Test void serializedPublishRoutesByActualChannelRatherThanSubscriptionPattern() throws Exception {
        var connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connection.afterPropertiesSet(); connection.start();
        var registry = mock(SseEmitterRegistry.class);
        var json = new ObjectMapper();
        var config = new RedisConfig();
        var meters = new SimpleMeterRegistry();
        var messages = config.redisPubSubMessageExecutor(meters, 2, 8);
        var subscriptions = config.redisPubSubSubscriptionExecutor();
        var listener = config.listenerContainer(connection, new RedisNotificationSubscriber(registry, json), messages, subscriptions);
        listener.afterPropertiesSet(); listener.start();
        try {
            var publisher = new RedisNotificationPublisher(new StringRedisTemplate(connection));
            var payload = NotificationPayload.builder().notificationId(5L).message("승인")
                    .createdAt(LocalDateTime.now().toString()).build();
            publisher.publishSerialized(99L, json.writeValueAsString(payload));
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> verify(registry).send(eq(99L),
                    argThat(received -> received.getNotificationId().equals(5L))));
        } finally {
            listener.destroy(); messages.shutdownNow(); subscriptions.shutdownNow();
            connection.destroy(); meters.close();
        }
    }
}
