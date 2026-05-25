package com.techeer.carpool.domain.notification.entity;

import com.techeer.carpool.domain.notification.type.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_receiver_id", columnList = "receiver_id"),
        @Index(name = "idx_notification_receiver_read", columnList = "receiver_id, read_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private Long receiverId;

    private Long referenceId;

    @Column(nullable = false, length = 100)
    private String message;

    private LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    private Notification(NotificationType type, Long receiverId, Long referenceId, String message) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(receiverId, "receiverId must not be null");
        Objects.requireNonNull(message, "message must not be null");
        this.type = type;
        this.receiverId = receiverId;
        this.referenceId = referenceId;
        this.message = message;
    }

    public static Notification ofApplicationReceived(Long receiverId, Long postId) {
        return Notification.builder()
                .type(NotificationType.APPLICATION_RECEIVED)
                .receiverId(receiverId)
                .referenceId(postId)
                .message("카풀 신청이 도착했습니다.")
                .build();
    }

    public static Notification ofApplicationAccepted(Long receiverId, Long postId) {
        return Notification.builder()
                .type(NotificationType.APPLICATION_ACCEPTED)
                .receiverId(receiverId)
                .referenceId(postId)
                .message("카풀 신청이 승인되었습니다.")
                .build();
    }

    public static Notification ofApplicationRejected(Long receiverId, Long postId) {
        return Notification.builder()
                .type(NotificationType.APPLICATION_REJECTED)
                .receiverId(receiverId)
                .referenceId(postId)
                .message("카풀 신청이 거절되었습니다.")
                .build();
    }

    public static Notification ofRideStarted(Long receiverId, Long rideId) {
        return Notification.builder()
                .type(NotificationType.RIDE_STARTED)
                .receiverId(receiverId)
                .referenceId(rideId)
                .message("카풀 운행이 시작되었습니다.")
                .build();
    }

    public static Notification ofRideEnded(Long receiverId, Long rideId) {
        return Notification.builder()
                .type(NotificationType.RIDE_ENDED)
                .receiverId(receiverId)
                .referenceId(rideId)
                .message("카풀 운행이 종료되었습니다.")
                .build();
    }

    public static Notification ofPostCancelled(Long receiverId, Long postId) {
        return Notification.builder()
                .type(NotificationType.POST_CANCELLED)
                .receiverId(receiverId)
                .referenceId(postId)
                .message("신청한 카풀 게시글이 취소되었습니다.")
                .build();
    }

    public static Notification ofDepartureApproaching(Long receiverId, Long postId) {
        return Notification.builder()
                .type(NotificationType.DEPARTURE_APPROACHING)
                .receiverId(receiverId)
                .referenceId(postId)
                .message("1시간 후 출발 예정입니다. 카풀을 마감해 주세요.")
                .build();
    }

    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
