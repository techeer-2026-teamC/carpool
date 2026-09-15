package com.techeer.carpool.domain.notification;

import com.techeer.carpool.domain.notification.entity.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(NotificationDatabaseTestSupport.DatabaseConfiguration.class)
class NotificationPersistenceIntegrationTest extends NotificationDatabaseTestSupport {
    @Test void businessRollbackAlsoRollsBackNotificationAndOutbox() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.save(Notification.ofApplicationAccepted(10L, 20L));
            throw new IllegalStateException("approval failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(notifications.count()).isZero();
        assertThat(outboxes.count()).isZero();
        tx.executeWithoutResult(status -> service.save(Notification.ofApplicationAccepted(10L, 20L)));
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(outboxes.count()).isEqualTo(1);
        assertThat(outboxes.findAll().get(0).getNotificationId()).isEqualTo(notifications.findAll().get(0).getNotificationId());
    }
    @Test void inboxCursorReadOwnershipAndRepeatedReadAreConsistent() {
        service.saveAll(List.of(Notification.ofApplicationAccepted(10L, 20L),
                Notification.ofApplicationRejected(10L, 21L), Notification.ofApplicationAccepted(11L, 22L)));
        var newest = service.getInbox(10L, null, 1);
        assertThat(newest.hasNext()).isTrue();
        var oldest = service.getInbox(10L, newest.nextCursor(), 1);
        assertThat(oldest.hasNext()).isFalse();
        assertThat(oldest.items().get(0).getNotificationId()).isLessThan(newest.items().get(0).getNotificationId());
        long id = newest.items().get(0).getNotificationId();
        assertThatThrownBy(() -> service.markAsRead(11L, id)).isInstanceOf(com.techeer.carpool.global.exception.CarpoolException.class);
        service.markAsRead(10L, id);
        String readAt = service.getInbox(10L, null, 1).items().get(0).getReadAt();
        service.markAsRead(10L, id);
        assertThat(service.getInbox(10L, null, 1).items().get(0).getReadAt()).isEqualTo(readAt);
        assertThat(service.getUnreadCount(10L)).isEqualTo(1);
    }
}
