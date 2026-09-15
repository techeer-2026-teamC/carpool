package com.techeer.carpool.domain.notification.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.emitter.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;

class RedisNotificationSubscriberTest {
    @Test
    void invalidMessagesDoNotReachUsersOrPreventTheNextValidDelivery() throws Exception {
        var registry = mock(SseEmitterRegistry.class);
        var json = new ObjectMapper();
        var subscriber = new RedisNotificationSubscriber(registry, json);
        subscriber.onMessage("{}", "notification:99");
        subscriber.onMessage("{\"notificationId\":5}", "notification:99");
        subscriber.onMessage("not-json", "notification:99");
        subscriber.onMessage("{}", "notification:*");
        subscriber.onMessage("{}", "other:99");
        verifyNoInteractions(registry);

        var payload = NotificationPayload.builder().notificationId(5L).message("승인")
                .createdAt(LocalDateTime.now().toString()).build();
        subscriber.onMessage(json.writeValueAsString(payload), "notification:99");
        verify(registry).send(eq(99L), argThat(received -> received.getNotificationId().equals(5L)));
        verifyNoMoreInteractions(registry);
    }
}
