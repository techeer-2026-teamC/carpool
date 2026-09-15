package com.techeer.carpool.domain.notification.service;

import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.dto.NotificationPage;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.outbox.NotificationOutbox;
import com.techeer.carpool.domain.notification.outbox.NotificationOutboxRepository;
import com.techeer.carpool.domain.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(Notification notification) {
        notificationRepository.save(notification);
        try {
            String payload = objectMapper.writeValueAsString(NotificationPayload.from(notification));
            outboxRepository.save(new NotificationOutbox(notification.getNotificationId(),
                    notification.getReceiverId(), payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("알림 직렬화 실패: 업무 트랜잭션을 롤백합니다.", e);
        }
    }

    @Transactional
    public void saveAll(List<Notification> notifications) {
        notifications.forEach(this::save);
    }

    public NotificationPage getInbox(Long receiverId, Long beforeId, int size) {
        if (size < 1 || size > 100 || (beforeId != null && beforeId < 1)) {
            throw new CarpoolException(ErrorCode.INVALID_INPUT);
        }
        List<Notification> rows = notificationRepository.findInbox(receiverId, beforeId, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<NotificationPayload> items = rows.stream().limit(size).map(NotificationPayload::from).toList();
        Long nextCursor = hasNext ? items.get(items.size() - 1).getNotificationId() : null;
        return new NotificationPage(items, nextCursor, hasNext);
    }

    public List<Notification> getNotifications(Long receiverId) {
        return notificationRepository.findByReceiverIdOrderByCreatedAtDesc(receiverId);
    }

    public long getUnreadCount(Long receiverId) {
        return notificationRepository.countByReceiverIdAndReadAtIsNull(receiverId);
    }

    @Transactional
    public void markAsRead(Long receiverId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getReceiverId().equals(receiverId)) {
            throw new CarpoolException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
        notification.markAsRead();
    }
}
