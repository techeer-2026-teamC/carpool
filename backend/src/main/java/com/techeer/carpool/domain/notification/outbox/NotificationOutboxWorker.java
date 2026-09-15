package com.techeer.carpool.domain.notification.outbox;

import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class NotificationOutboxWorker {
    private final OutboxDeliveryStore store;
    private final RedisNotificationPublisher publisher;
    private final MeterRegistry meters;
    private volatile OutboxDeliveryStore.Backlog backlog = new OutboxDeliveryStore.Backlog(0, 0, 0);
    @Value("${notification.outbox.lease-ms:30000}")
    private long leaseMillis;
    @Value("${notification.outbox.batch-size:100}")
    private int batchSize;
    @Value("${notification.outbox.retention-days:7}")
    private long retentionDays;

    public NotificationOutboxWorker(OutboxDeliveryStore store, RedisNotificationPublisher publisher, MeterRegistry meters) {
        this.store = store;
        this.publisher = publisher;
        this.meters = meters;
        meters.gauge("notification.outbox.pending", this, w -> w.backlog.pending());
        meters.gauge("notification.outbox.retrying", this, w -> w.backlog.retrying());
        meters.gauge("notification.outbox.oldest.seconds", this, w -> w.backlog.oldestSeconds());
    }

    @PostConstruct
    void validate() {
        if (leaseMillis < 1000 || batchSize < 1 || batchSize > 1000 || retentionDays < 1) {
            throw new IllegalArgumentException("Invalid notification outbox configuration");
        }
    }

    @Scheduled(fixedDelayString = "${notification.outbox.poll-ms:200}")
    public void publishAvailable() {
        for (int i = 0; i < batchSize; i++) {
            var delivery = store.claim(leaseMillis);
            if (delivery.isEmpty()) return;
            if (!publish(delivery.get())) return;
        }
    }

    boolean publish(OutboxDeliveryStore.Delivery delivery) {
        try {
            publisher.publishSerialized(delivery.receiverId(), delivery.payload());
            // A publish followed by a crash can repeat. Stable notificationId lets clients deduplicate.
            if (store.markPublished(delivery)) {
                meters.counter("notification.outbox.publish", "outcome", "success").increment();
                meters.timer("notification.outbox.publish.delay").record(
                        Duration.ofMillis(Math.max(0, Duration.between(delivery.createdAt(), LocalDateTime.now()).toMillis())));
            }
            return true;
        } catch (RuntimeException e) {
            meters.counter("notification.outbox.publish", "outcome", "failure").increment();
            store.retry(delivery, backoffMillis(delivery.attempts()));
            log.warn("Notification publication failed; retained for retry, outboxId={}", delivery.id());
            return false;
        }
    }

    static long backoffMillis(int attempts) {
        return Math.min(60_000, 200L * (1L << Math.min(9, Math.max(0, attempts - 1))));
    }

    @Scheduled(fixedDelayString = "${notification.outbox.metrics-ms:5000}")
    public void updateBacklog() {
        backlog = store.backlog();
    }

    @Scheduled(fixedDelayString = "${notification.outbox.cleanup-ms:60000}")
    public void cleanPublished() {
        store.removePublished(retentionDays);
    }
}
