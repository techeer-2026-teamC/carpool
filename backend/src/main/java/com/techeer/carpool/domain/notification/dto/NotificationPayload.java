package com.techeer.carpool.domain.notification.dto;

import com.techeer.carpool.domain.notification.type.NotificationType;
import com.techeer.carpool.domain.notification.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
    private Long notificationId;
    private NotificationType type;
    private String message;
    private Long referenceId;
    private String createdAt;
    private String readAt;
    private Map<String, Object> data;

    public static NotificationPayload from(Notification notification) {
        return NotificationPayload.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getType())
                .message(notification.getMessage())
                .referenceId(notification.getReferenceId())
                .createdAt(notification.getCreatedAt().toString())
                .readAt(notification.getReadAt() == null ? null : notification.getReadAt().toString())
                .data(notification.getReferenceId() == null ? Map.of()
                        : Map.of("referenceId", notification.getReferenceId()))
                .build();
    }
}
