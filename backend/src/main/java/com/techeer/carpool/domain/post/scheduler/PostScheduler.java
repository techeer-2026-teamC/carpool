package com.techeer.carpool.domain.post.scheduler;

import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.post.entity.PostStatus;
import com.techeer.carpool.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class PostScheduler {
    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${app.recruitment.close-interval-ms:1000}")
    public void autoCloseExpiredPosts() {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : postRepository.findExpiredPostIds(now)) {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                postRepository.findByIdAndDeletedFalseWithLock(id).ifPresent(post -> {
                    if (post.getStatus() == PostStatus.OPEN && !post.getDepartureTime().isAfter(now)) post.close();
                }));
        }
    }

    @Scheduled(fixedDelayString = "${app.recruitment.reminder-interval-ms:10000}")
    public void notifyApproachingDeparture() {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : postRepository.findApproachingPostIds(now, now.plusHours(1))) {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                postRepository.findByIdAndDeletedFalseWithLock(id).ifPresent(post -> {
                    if (post.getStatus() != PostStatus.CANCELLED && post.getDepartureNotifiedAt() == null && post.getDepartureTime().isAfter(now) && !post.getDepartureTime().isAfter(now.plusHours(1))
                            && post.getMeetingCompletedAt() == null) {
                        notificationService.save(Notification.ofDepartureApproaching(post.getMemberId(), id));
                        post.markDepartureNotified(now);
                    }
                }));
        }
    }
}
