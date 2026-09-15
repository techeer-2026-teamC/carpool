package com.techeer.carpool.domain.notification.emitter;

import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "api", matchIfMissing = true)
public class SseEmitterRegistry {
    private final Map<Long, Map<String, Session>> sessions = new ConcurrentHashMap<>();
    private final Semaphore permits;
    private final ThreadPoolExecutor senders;
    private final MeterRegistry meters;
    private final int maxConnections;
    private final int maxPerUser;
    private final int queueCapacity;
    private final long timeoutMillis;

    public SseEmitterRegistry(MeterRegistry meters,
                             @Value("${notification.sse.max-connections:10000}") int maxConnections,
                             @Value("${notification.sse.max-per-user:5}") int maxPerUser,
                             @Value("${notification.sse.queue-capacity:32}") int queueCapacity,
                             @Value("${notification.sse.timeout-ms:300000}") long timeoutMillis,
                             @Value("${notification.sse.send-threads:8}") int sendThreads,
                             @Value("${notification.sse.send-queue-capacity:1024}") int sendQueueCapacity) {
        if (maxConnections < 1 || maxPerUser < 1 || queueCapacity < 1 || timeoutMillis < 1
                || sendThreads < 1 || sendQueueCapacity < 1) {
            throw new IllegalArgumentException("Invalid SSE bounds");
        }
        this.meters = meters;
        this.maxConnections = maxConnections;
        this.maxPerUser = maxPerUser;
        this.queueCapacity = queueCapacity;
        this.timeoutMillis = timeoutMillis;
        this.permits = new Semaphore(maxConnections);
        this.senders = new ThreadPoolExecutor(sendThreads, sendThreads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(sendQueueCapacity), task -> {
                    Thread thread = new Thread(task, "notification-sse-send");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        meters.gauge("notification.sse.connections", this, r -> r.maxConnections - r.permits.availablePermits());
        meters.gauge("notification.sse.users", sessions, Map::size);
        meters.gauge("notification.sse.sender.queue", senders, e -> e.getQueue().size());
    }

    public SseEmitter subscribe(Long userId) {
        return subscribe(userId, timeoutMillis);
    }

    public SseEmitter subscribe(Long userId, long authenticationRemainingMillis) {
        if (authenticationRemainingMillis <= 0) throw new IllegalArgumentException("Expired SSE authentication");
        if (!permits.tryAcquire()) throw new CapacityExceededException();
        Session session = new Session(userId, createEmitter(Math.min(timeoutMillis, authenticationRemainingMillis)));
        try {
            sessions.compute(userId, (id, current) -> {
                Map<String, Session> connections = current == null ? new ConcurrentHashMap<>() : current;
                if (connections.size() >= maxPerUser) throw new CapacityExceededException();
                connections.put(session.id, session);
                return connections;
            });
        } catch (RuntimeException e) {
            permits.release();
            throw e;
        }
        session.emitter.onCompletion(() -> session.close(null));
        session.emitter.onTimeout(() -> session.close(null));
        session.emitter.onError(session::close);
        session.offer(SseEmitter.event().name("connect").data("connected"), null);
        return session.emitter;
    }

    protected SseEmitter createEmitter(long timeout) {
        return new SseEmitter(timeout);
    }

    public void send(Long userId, NotificationPayload payload) {
        Map<String, Session> connections = sessions.get(userId);
        if (connections == null) return;
        connections.values().forEach(session -> session.offer(SseEmitter.event()
                .id(payload.getNotificationId().toString()).name("notification").data(payload), payload.getCreatedAt()));
    }

    @Scheduled(fixedDelayString = "${notification.sse.heartbeat-ms:15000}")
    public void heartbeat() {
        sessions.values().forEach(connections -> connections.values().forEach(session ->
                session.offer(SseEmitter.event().name("heartbeat").data("alive"), null)));
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(connections -> connections.values().forEach(session -> session.close(null)));
        senders.shutdownNow();
    }

    public static class CapacityExceededException extends RuntimeException { }
    private record Pending(SseEmitter.SseEventBuilder event, String createdAt) { }

    private final class Session {
        final String id = UUID.randomUUID().toString();
        final Long userId;
        final SseEmitter emitter;
        final ArrayBlockingQueue<Pending> pending = new ArrayBlockingQueue<>(queueCapacity);
        final AtomicBoolean draining = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();

        Session(Long userId, SseEmitter emitter) {
            this.userId = userId;
            this.emitter = emitter;
        }

        void offer(SseEmitter.SseEventBuilder event, String createdAt) {
            if (closed.get()) return;
            if (!pending.offer(new Pending(event, createdAt))) {
                meters.counter("notification.sse.closed", "reason", "backpressure").increment();
                close(new IllegalStateException("SSE client is too slow; reconnect and reconcile inbox"));
                return;
            }
            if (closed.get()) {
                pending.clear();
                return;
            }
            scheduleDrain();
        }

        void scheduleDrain() {
            if (!closed.get() && draining.compareAndSet(false, true)) {
                try {
                    senders.execute(this::drain);
                } catch (RejectedExecutionException e) {
                    draining.set(false);
                    meters.counter("notification.sse.closed", "reason", "executor_capacity").increment();
                    close(e);
                }
            }
        }

        void drain() {
            try {
                Pending next;
                while (!closed.get() && (next = pending.poll()) != null) {
                    emitter.send(next.event());
                    if (next.createdAt() != null) {
                        meters.timer("notification.sse.send.delay").record(Duration.ofMillis(Math.max(0,
                                Duration.between(LocalDateTime.parse(next.createdAt()), LocalDateTime.now()).toMillis())));
                    }
                    meters.counter("notification.sse.sent").increment();
                }
            } catch (IOException | RuntimeException e) {
                meters.counter("notification.sse.closed", "reason", "send_error").increment();
                close(e);
            } finally {
                draining.set(false);
                if (!pending.isEmpty()) scheduleDrain();
            }
        }

        void close(Throwable error) {
            if (!closed.compareAndSet(false, true)) return;
            pending.clear();
            sessions.computeIfPresent(userId, (user, connections) -> {
                connections.remove(id, this);
                return connections.isEmpty() ? null : connections;
            });
            permits.release();
            if (error == null) emitter.complete();
            else emitter.completeWithError(error);
        }
    }
}
