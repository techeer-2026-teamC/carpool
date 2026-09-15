package com.techeer.carpool.domain.notification.outbox;

import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationOutboxWorkerTest {
    @Test
    void publishFailureSchedulesRetryWithoutAcknowledgingDelivery() {
        var store = mock(OutboxDeliveryStore.class);
        var publisher = mock(RedisNotificationPublisher.class);
        var delivery = new OutboxDeliveryStore.Delivery(1, 2, "payload", "lease", 3, LocalDateTime.now());
        doThrow(new IllegalStateException("Redis unavailable")).when(publisher).publishSerialized(2L, "payload");
        var worker = new NotificationOutboxWorker(store, publisher, new SimpleMeterRegistry());
        worker.publish(delivery);
        verify(store).retry(delivery, 800);
        verify(store, never()).markPublished(delivery);
    }

    @Test
    void firstPublishFailureStopsBatchBeforeClaimingAnotherNotification() {
        var store = mock(OutboxDeliveryStore.class);
        var publisher = mock(RedisNotificationPublisher.class);
        var first = new OutboxDeliveryStore.Delivery(1, 2, "first", "lease-1", 1, LocalDateTime.now());
        var second = new OutboxDeliveryStore.Delivery(2, 3, "second", "lease-2", 1, LocalDateTime.now());
        when(store.claim(30_000)).thenReturn(Optional.of(first)).thenReturn(Optional.of(second)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Redis unavailable")).when(publisher).publishSerialized(2L, "first");
        var worker = new NotificationOutboxWorker(store, publisher, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(worker, "batchSize", 100);
        ReflectionTestUtils.setField(worker, "leaseMillis", 30_000L);

        worker.publishAvailable();

        verify(store, times(1)).claim(30_000);
        verify(store).retry(first, 200);
        verify(store, never()).markPublished(any());
        verify(publisher, never()).publishSerialized(3L, "second");
    }

    @Test
    void successfulBatchContinuesUntilNoNotificationIsAvailable() {
        var store = mock(OutboxDeliveryStore.class);
        var publisher = mock(RedisNotificationPublisher.class);
        var first = new OutboxDeliveryStore.Delivery(1, 2, "first", "lease-1", 1, LocalDateTime.now());
        var second = new OutboxDeliveryStore.Delivery(2, 3, "second", "lease-2", 1, LocalDateTime.now());
        when(store.claim(30_000)).thenReturn(Optional.of(first)).thenReturn(Optional.of(second)).thenReturn(Optional.empty());
        when(store.markPublished(any())).thenReturn(true);
        var worker = new NotificationOutboxWorker(store, publisher, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(worker, "batchSize", 100);
        ReflectionTestUtils.setField(worker, "leaseMillis", 30_000L);

        worker.publishAvailable();

        verify(store, times(3)).claim(30_000);
        verify(publisher).publishSerialized(2L, "first");
        verify(publisher).publishSerialized(3L, "second");
        verify(store, never()).retry(any(), anyLong());
    }

    @Test
    void retryDelayIsBoundedEvenAfterProlongedFailure() {
        assertThat(NotificationOutboxWorker.backoffMillis(1)).isEqualTo(200);
        assertThat(NotificationOutboxWorker.backoffMillis(4)).isEqualTo(1600);
        assertThat(NotificationOutboxWorker.backoffMillis(Integer.MAX_VALUE)).isEqualTo(60_000);
    }
}
