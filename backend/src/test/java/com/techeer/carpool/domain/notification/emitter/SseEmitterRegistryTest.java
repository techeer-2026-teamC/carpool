package com.techeer.carpool.domain.notification.emitter;

import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class SseEmitterRegistryTest {
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    Registry registry;
    @AfterEach void stop() { if (registry != null) registry.shutdown(); }

    @Test
    void completionOfOldTabDoesNotRemoveAnotherTabOrReplacement() {
        registry = new Registry(false, 5);
        Emitter old = (Emitter) registry.subscribe(1L);
        Emitter other = (Emitter) registry.subscribe(1L);
        old.completion.run();
        Emitter replacement = (Emitter) registry.subscribe(1L);
        old.completion.run();
        registry.send(1L, payload());
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(other.notifications.get()).isEqualTo(1);
            assertThat(replacement.notifications.get()).isEqualTo(1);
        });
        assertThat(old.notifications.get()).isZero();
        assertThat(connections()).isEqualTo(2);
        other.timeout.run();
        replacement.error.accept(new IOException("connection ended"));
        assertThat(connections()).isZero();
        assertThat(meters.get("notification.sse.users").gauge().value()).isZero();
    }

    @Test
    void slowClientIsRemovedWhenItsBoundedQueueFills() throws Exception {
        registry = new Registry(true, 5);
        Emitter blocked = (Emitter) registry.subscribe(1L);
        assertThat(blocked.entered.await(2, TimeUnit.SECONDS)).isTrue();
        registry.send(1L, payload());
        registry.send(1L, payload());
        registry.send(1L, payload());
        assertThat(connections()).isZero();
        assertThat(meters.get("notification.sse.closed").tag("reason", "backpressure").counter().count()).isEqualTo(1);
        blocked.release.countDown();
    }

    @Test
    void perUserLimitDoesNotLeakGlobalCapacityAndTimeoutIsCappedAtTokenExpiry() {
        registry = new Registry(false, 1);
        Emitter first = (Emitter) registry.subscribe(1L, 1000);
        assertThat(first.getTimeout()).isEqualTo(1000);
        assertThatThrownBy(() -> registry.subscribe(1L)).isInstanceOf(SseEmitterRegistry.CapacityExceededException.class);
        assertThat(connections()).isEqualTo(1);
        first.completion.run();
        registry.subscribe(1L);
        assertThat(connections()).isEqualTo(1);
    }

    NotificationPayload payload() {
        return NotificationPayload.builder().notificationId(5L).message("approved")
                .createdAt(LocalDateTime.now().toString()).build();
    }

    double connections() { return meters.get("notification.sse.connections").gauge().value(); }

    class Registry extends SseEmitterRegistry {
        final boolean block;
        Registry(boolean block, int maxPerUser) {
            super(meters, 10, maxPerUser, 2, 300000, 1, 2);
            this.block = block;
        }
        @Override protected SseEmitter createEmitter(long timeout) { return new Emitter(timeout, block); }
    }

    static class Emitter extends SseEmitter {
        Runnable completion;
        Runnable timeout;
        Consumer<Throwable> error;
        final AtomicInteger notifications = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release;
        Emitter(long timeout, boolean block) { super(timeout); release = new CountDownLatch(block ? 1 : 0); }
        @Override public void onCompletion(Runnable callback) { completion = callback; }
        @Override public void onTimeout(Runnable callback) { timeout = callback; }
        @Override public void onError(Consumer<Throwable> callback) { error = callback; }
        @Override public void complete() { if (completion != null) completion.run(); }
        @Override public void completeWithError(Throwable ex) { if (error != null) error.accept(ex); }
        @Override public void send(SseEventBuilder event) throws IOException {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test send did not resume");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            event.build().forEach(data -> { if (data.getData() instanceof NotificationPayload) notifications.incrementAndGet(); });
        }
    }
}
