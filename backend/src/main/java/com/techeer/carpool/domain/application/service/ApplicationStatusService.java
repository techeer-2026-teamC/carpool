package com.techeer.carpool.domain.application.service;

import com.techeer.carpool.domain.application.dto.ApplicationResponse;
import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.notification.type.NotificationType;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApplicationStatusService {

    private final ApplicationRepository applicationRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final RedisNotificationPublisher notificationPublisher;
    private final NotificationService notificationService;
    private final CarpoolMetrics carpoolMetrics;

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 1.5, random = true)
    )
    @Transactional
    public ApplicationResponse accept(Long applicationId, Long requesterId) {
        Application application = findPending(applicationId);

        Post post = postRepository.findByIdAndDeletedFalseWithLock(application.getPostId())
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMemberId().equals(requesterId)) {
            throw new CarpoolException(ErrorCode.APPLICATION_FORBIDDEN);
        }

        if (post.isFull()) {
            throw new CarpoolException(ErrorCode.APPLICATION_POST_FULL);
        }

        application.accept();
        post.incrementPassengers();
        carpoolMetrics.incrementApplicationAccepted();

        notificationService.save(Notification.ofApplicationAccepted(application.getApplicantId(), application.getPostId()));
        notificationPublisher.publish(application.getApplicantId(), NotificationPayload.builder()
                .type(NotificationType.APPLICATION_ACCEPTED)
                .message("카풀 신청이 승인되었습니다.")
                .data(Map.of("postId", application.getPostId()))
                .build());

        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse reject(Long applicationId, Long requesterId) {
        Application application = findPending(applicationId);

        Post post = postRepository.findByIdAndDeletedFalse(application.getPostId())
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMemberId().equals(requesterId)) {
            throw new CarpoolException(ErrorCode.APPLICATION_FORBIDDEN);
        }

        application.reject();
        carpoolMetrics.incrementApplicationRejected();

        notificationService.save(Notification.ofApplicationRejected(application.getApplicantId(), application.getPostId()));
        notificationPublisher.publish(application.getApplicantId(), NotificationPayload.builder()
                .type(NotificationType.APPLICATION_REJECTED)
                .message("카풀 신청이 거절되었습니다.")
                .data(Map.of("postId", application.getPostId()))
                .build());

        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse cancelAccept(Long applicationId, Long requesterId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new CarpoolException(ErrorCode.APPLICATION_NOT_ACCEPTED);
        }

        Post post = postRepository.findByIdAndDeletedFalse(application.getPostId())
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMemberId().equals(requesterId)) {
            throw new CarpoolException(ErrorCode.APPLICATION_FORBIDDEN);
        }

        application.resetToPending();
        post.decrementPassengers();

        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse cancelReject(Long applicationId, Long requesterId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != ApplicationStatus.REJECTED) {
            throw new CarpoolException(ErrorCode.APPLICATION_NOT_REJECTED);
        }

        Post post = postRepository.findByIdAndDeletedFalse(application.getPostId())
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (!post.getMemberId().equals(requesterId)) {
            throw new CarpoolException(ErrorCode.APPLICATION_FORBIDDEN);
        }

        application.resetToPending();

        return toResponse(application);
    }

    private Application findPending(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new CarpoolException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        return application;
    }

    private ApplicationResponse toResponse(Application application) {
        String nickname = memberRepository.findById(application.getApplicantId())
                .map(Member::getNickname)
                .orElse("알 수 없음");
        return ApplicationResponse.of(application, nickname);
    }
}
