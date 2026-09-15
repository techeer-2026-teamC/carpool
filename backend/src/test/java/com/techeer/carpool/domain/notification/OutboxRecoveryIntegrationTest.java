package com.techeer.carpool.domain.notification;

import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.outbox.OutboxDeliveryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig({NotificationDatabaseTestSupport.DatabaseConfiguration.class, OutboxDeliveryStore.class})
class OutboxRecoveryIntegrationTest extends NotificationDatabaseTestSupport {
    @Autowired OutboxDeliveryStore store;
    private void saveReadyNotification() {
        service.save(Notification.ofApplicationAccepted(10L, 20L));
        // Readiness uses the database clock; the host JVM and Docker may differ slightly.
        jdbc.update("UPDATE notification_outbox SET available_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
    }
    @Test void uncommittedNotificationCannotBeClaimed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            saveReadyNotification();
            assertThat(store.claim(30_000)).isEmpty();
        });
        assertThat(store.claim(30_000)).isPresent();
    }
    @Test void competingWorkersClaimOnceAndExpiredOwnerCannotAcknowledgeNewLease() throws Exception {
        saveReadyNotification();
        var threads = Executors.newFixedThreadPool(2);
        try {
            var first = threads.submit(() -> store.claim(30_000));
            var second = threads.submit(() -> store.claim(30_000));
            var claims = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertThat(claims.stream().filter(java.util.Optional::isPresent).count()).isEqualTo(1);
            var expired = claims.stream().flatMap(java.util.Optional::stream).findFirst().orElseThrow();
            jdbc.update("UPDATE notification_outbox SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
            var recovered = store.claim(30_000).orElseThrow();
            assertThat(recovered.payload()).isEqualTo(expired.payload());
            assertThat(recovered.claimToken()).isNotEqualTo(expired.claimToken());
            assertThat(store.markPublished(expired)).isFalse();
            assertThat(store.markPublished(recovered)).isTrue();
        } finally { threads.shutdownNow(); }
    }
    @Test void retryBackoffRetainsInboxAndDoesNotReclaimBeforeAvailability() {
        saveReadyNotification();
        var delivery = store.claim(30_000).orElseThrow();
        store.retry(delivery, 60_000);
        assertThat(store.claim(30_000)).isEmpty();
        assertThat(service.getUnreadCount(10L)).isEqualTo(1);
        assertThat(store.backlog().pending()).isEqualTo(1);
        assertThat(store.backlog().retrying()).isEqualTo(1);
    }
}
