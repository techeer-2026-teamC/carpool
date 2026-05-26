package com.techeer.carpool.domain.application.service;

import com.techeer.carpool.domain.application.dto.ApplicationResponse;
import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.notification.type.NotificationType;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostStatus;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApplicationCreateService {

    private final ApplicationRepository applicationRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final RedisNotificationPublisher notificationPublisher;
    private final NotificationService notificationService;
    private final CarpoolMetrics carpoolMetrics;

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 1.5, random = true)
    )
    @Transactional
    public ApplicationResponse apply(Long postId, Long applicantId) {
        Post post = postRepository.findByIdAndDeletedFalseWithLock(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (post.getMemberId().equals(applicantId)) {
            throw new CarpoolException(ErrorCode.APPLICATION_SELF);
        }

        if (post.getStatus() == PostStatus.CLOSED) {
            throw new CarpoolException(ErrorCode.APPLICATION_POST_FULL);
        }

        if (applicationRepository.existsByPostIdAndApplicantId(postId, applicantId)) {
            throw new CarpoolException(ErrorCode.APPLICATION_DUPLICATE);
        }

        Application application = Application.builder()
                .postId(postId)
                .applicantId(applicantId)
                .build();

        if (post.isAutoAccept()) {
            if (post.isFull()) {
                throw new CarpoolException(ErrorCode.APPLICATION_POST_FULL);
            }
            application.accept();
            post.incrementPassengers();
        }

        Application saved;
        try {
            saved = applicationRepository.save(application);
        } catch (DataIntegrityViolationException e) {
            throw new CarpoolException(ErrorCode.APPLICATION_DUPLICATE);
        }
        carpoolMetrics.incrementApplicationSubmitted();

        String nickname = memberRepository.findById(applicantId)
                .map(Member::getNickname)
                .orElse("알 수 없음");

        notificationService.save(Notification.ofApplicationReceived(post.getMemberId(), postId));
        notificationPublisher.publish(post.getMemberId(), NotificationPayload.builder()
                .type(NotificationType.APPLICATION_RECEIVED)
                .message(nickname + "님이 카풀을 신청했습니다.")
                .data(Map.of("postId", postId, "applicationId", saved.getId()))
                .build());

        return ApplicationResponse.of(saved, nickname);
    }
}
