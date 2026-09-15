package com.techeer.carpool.domain.notification.repository;

import com.techeer.carpool.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    @Query("select n from Notification n where n.receiverId = :receiverId " +
            "and (:beforeId is null or n.notificationId < :beforeId) order by n.notificationId desc")
    List<Notification> findInbox(Long receiverId, Long beforeId, Pageable pageable);

    long countByReceiverIdAndReadAtIsNull(Long receiverId);
}
