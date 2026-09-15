package com.techeer.carpool.domain.notification.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_outbox", indexes = {
        @Index(name = "idx_outbox_delivery", columnList = "published_at,available_at,lease_until")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long notificationId;
    @Column(nullable = false)
    private Long receiverId;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private LocalDateTime availableAt;
    private LocalDateTime leaseUntil;
    @Column(length = 36)
    private String claimToken;
    private LocalDateTime publishedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public NotificationOutbox(Long notificationId, Long receiverId, String payload) {
        this.notificationId = notificationId;
        this.receiverId = receiverId;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
        this.availableAt = createdAt;
    }
}
