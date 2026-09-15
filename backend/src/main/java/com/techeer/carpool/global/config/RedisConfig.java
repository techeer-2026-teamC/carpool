package com.techeer.carpool.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.notification.subscriber.RedisNotificationSubscriber;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
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

    @Bean(defaultCandidate = false, destroyMethod = "shutdownNow")
    @ConditionalOnProperty(name = "app.role", havingValue = "api", matchIfMissing = true)
    public ThreadPoolExecutor redisPubSubMessageExecutor(MeterRegistry meters,
            @Value("${notification.pubsub.threads:8}") int threads,
            @Value("${notification.pubsub.queue-capacity:1024}") int queueCapacity) {
        if (threads < 1 || queueCapacity < 1) throw new IllegalArgumentException("Invalid Pub/Sub executor bounds");
        AtomicInteger sequence = new AtomicInteger();
        AtomicLong nextWarningAt = new AtomicLong();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), task -> new Thread(task, "redis-pubsub-message-" + sequence.incrementAndGet()),
                (task, pool) -> {
                    meters.counter("redis.pubsub.rejected", "reason", pool.isShutdown() ? "shutdown" : "capacity").increment();
                    long now = System.currentTimeMillis();
                    long next = nextWarningAt.get();
                    if (now >= next && nextWarningAt.compareAndSet(next, now + 10_000)) {
                        log.warn("Pub/Sub executor saturated: live update dropped; clients must reconcile durable inbox/latest state");
                    }
                    // Pub/Sub is a live hint. Bound memory and record drops; the durable inbox repairs notification loss.
                });
        meters.gauge("redis.pubsub.executor.queued", executor, pool -> pool.getQueue().size());
        meters.gauge("redis.pubsub.executor.active", executor, ThreadPoolExecutor::getActiveCount);
        return executor;
    }

    @Bean(defaultCandidate = false, destroyMethod = "shutdownNow")
    @ConditionalOnProperty(name = "app.role", havingValue = "api", matchIfMissing = true)
    public ThreadPoolExecutor redisPubSubSubscriptionExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        // Subscription/recovery tasks must not compete with message fanout or be silently discarded.
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4),
                task -> new Thread(task, "redis-pubsub-subscription-" + sequence.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    // "notification:*" 패턴 구독 — 메시지 수신 시 RedisNotificationSubscriber.onMessage() 호출
    @Bean
    @ConditionalOnProperty(name = "app.role", havingValue = "api", matchIfMissing = true)
    public RedisMessageListenerContainer listenerContainer(
            RedisConnectionFactory factory,
            RedisNotificationSubscriber subscriber,
            com.techeer.carpool.domain.meeting.MeetingSocket.Fanout meetingFanout,
            @Qualifier("redisPubSubMessageExecutor") ThreadPoolExecutor messageExecutor,
            @Qualifier("redisPubSubSubscriptionExecutor") ThreadPoolExecutor subscriptionExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.setTaskExecutor(messageExecutor);
        container.setSubscriptionExecutor(subscriptionExecutor);
        container.addMessageListener(
                subscriber,
                new PatternTopic("notification:*")
        );
        container.addMessageListener(meetingFanout, new org.springframework.data.redis.listener.ChannelTopic(com.techeer.carpool.domain.meeting.MeetingLocations.CHANNEL));
        return container;
    }
}
