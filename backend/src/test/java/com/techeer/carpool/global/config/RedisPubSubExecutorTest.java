package com.techeer.carpool.global.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class RedisPubSubExecutorTest {
    @Test
    void saturationBoundsThreadsAndQueueAndCountsDroppedMessage() throws Exception {
        var meters = new SimpleMeterRegistry();
        var config = new RedisConfig();
        var executor = config.redisPubSubMessageExecutor(meters, 1, 1);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), queuedFinished = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();
        try {
            executor.execute(() -> {
                started.countDown();
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(queuedFinished::countDown);
            executor.execute(() -> rejectedTaskRan.set(true));

            assertThat(executor.getPoolSize()).isEqualTo(1);
            assertThat(executor.getQueue()).hasSize(1);
            assertThat(meters.get("redis.pubsub.rejected").tag("reason", "capacity").counter().count()).isEqualTo(1);
            assertThat(rejectedTaskRan).isFalse();
            release.countDown();
            assertThat(queuedFinished.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
            meters.close();
        }
    }

    @Test
    void subscriptionExecutionIsSeparateFromSaturatedMessageFanout() throws Exception {
        var meters = new SimpleMeterRegistry();
        var config = new RedisConfig();
        var messages = config.redisPubSubMessageExecutor(meters, 1, 1);
        var subscriptions = config.redisPubSubSubscriptionExecutor();
        CountDownLatch release = new CountDownLatch(1), subscriptionRan = new CountDownLatch(1);
        try {
            messages.execute(() -> {
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            messages.execute(() -> {});
            subscriptions.execute(subscriptionRan::countDown);
            assertThat(subscriptionRan.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(subscriptions.getMaximumPoolSize()).isEqualTo(2);
            assertThat(subscriptions.getQueue().remainingCapacity()).isEqualTo(4);
        } finally {
            release.countDown();
            messages.shutdownNow();
            subscriptions.shutdownNow();
            meters.close();
        }
    }
}
