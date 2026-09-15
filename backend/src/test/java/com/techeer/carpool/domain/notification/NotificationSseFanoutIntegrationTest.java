package com.techeer.carpool.domain.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.emitter.SseEmitterRegistry;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.outbox.NotificationOutboxWorker;
import com.techeer.carpool.domain.notification.outbox.OutboxDeliveryStore;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.notification.subscriber.RedisNotificationSubscriber;
import com.techeer.carpool.global.config.RedisConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringJUnitConfig({NotificationDatabaseTestSupport.DatabaseConfiguration.class, OutboxDeliveryStore.class})
class NotificationSseFanoutIntegrationTest extends NotificationDatabaseTestSupport {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Autowired OutboxDeliveryStore store;

    @Test
    void committedNotificationReachesTwoApiRegistriesAndMultipleTabsWithStableId() throws Exception {
        var connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        var config = new RedisConfig();
        var meters = new SimpleMeterRegistry();
        var messages = config.redisPubSubMessageExecutor(meters, 2, 8);
        var subscriptions = config.redisPubSubSubscriptionExecutor();
        var firstApi = new RecordingRegistry();
        var secondApi = new RecordingRegistry();
        var json = new ObjectMapper();
        var firstListener = config.listenerContainer(connection,
                new RedisNotificationSubscriber(firstApi, json),
                org.mockito.Mockito.mock(com.techeer.carpool.domain.meeting.MeetingSocket.Fanout.class), messages, subscriptions);
        var secondListener = config.listenerContainer(connection,
                new RedisNotificationSubscriber(secondApi, json),
                org.mockito.Mockito.mock(com.techeer.carpool.domain.meeting.MeetingSocket.Fanout.class), messages, subscriptions);
        try {
            firstListener.afterPropertiesSet();
            secondListener.afterPropertiesSet();
            firstListener.start();
            secondListener.start();
            var firstTab = (RecordingEmitter) firstApi.subscribe(99L);
            var secondTab = (RecordingEmitter) firstApi.subscribe(99L);
            var remoteTab = (RecordingEmitter) secondApi.subscribe(99L);
            var unrelated = (RecordingEmitter) secondApi.subscribe(98L);
            var worker = new NotificationOutboxWorker(store,
                    new RedisNotificationPublisher(new StringRedisTemplate(connection)), meters);
            ReflectionTestUtils.setField(worker, "batchSize", 10);
            ReflectionTestUtils.setField(worker, "leaseMillis", 30_000L);

            service.save(Notification.ofApplicationAccepted(99L, 20L));
            jdbc.update("UPDATE notification_outbox SET available_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
            worker.publishAvailable();
            long notificationId = service.getInbox(99L, null, 20).items().get(0).getNotificationId();
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                for (var tab : List.of(firstTab, secondTab, remoteTab)) {
                    assertThat(tab.received).extracting(NotificationPayload::getNotificationId).containsExactly(notificationId);
                    assertThat(tab.frames).anyMatch(frame -> frame.contains("id:" + notificationId + "\n"));
                }
            });
            assertThat(unrelated.received).isEmpty();
            assertThat(store.backlog().pending()).isZero();
            assertThat(outboxes.findAll().get(0).getPublishedAt()).isNotNull();
        } finally {
            firstListener.destroy();
            secondListener.destroy();
            firstApi.shutdown();
            secondApi.shutdown();
            messages.shutdownNow();
            subscriptions.shutdownNow();
            connection.destroy();
            meters.close();
        }
    }

    static class RecordingRegistry extends SseEmitterRegistry {
        RecordingRegistry() { super(new SimpleMeterRegistry(), 100, 5, 32, 300000, 2, 64); }
        @Override protected SseEmitter createEmitter(long timeout) { return new RecordingEmitter(timeout); }
    }

    static class RecordingEmitter extends SseEmitter {
        final List<NotificationPayload> received = new CopyOnWriteArrayList<>();
        final List<String> frames = new CopyOnWriteArrayList<>();
        Runnable completion;
        RecordingEmitter(long timeout) { super(timeout); }
        @Override public void onCompletion(Runnable callback) { completion = callback; }
        @Override public void complete() { if (completion != null) completion.run(); }
        @Override public void send(SseEventBuilder event) {
            event.build().forEach(data -> {
                if (data.getData() instanceof NotificationPayload payload) received.add(payload);
                else if (data.getData() instanceof String frame) frames.add(frame);
            });
        }
    }
}
