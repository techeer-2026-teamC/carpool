package com.techeer.carpool.domain.application.service;

import com.techeer.carpool.domain.application.dto.ApplicationResponse;
import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import com.techeer.carpool.domain.application.event.ParticipantRemoved;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ApplicationStatusService {
    private final ApplicationRepository applicationRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final CarpoolMetrics carpoolMetrics;
    private final ApplicationEventPublisher events;

    @Transactional
    public ApplicationResponse accept(Long applicationId, Long requesterId) {
        Long applicantId = applicationRepository.findApplicantIdById(applicationId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.APPLICATION_NOT_FOUND));
        memberRepository.findActiveByIdWithLock(applicantId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));
        Post post = lockPost(applicationId);
        requireOwner(post, requesterId);
        Application application = findApplication(applicationId);
        requireStatus(application, ApplicationStatus.PENDING, ErrorCode.APPLICATION_ALREADY_PROCESSED);
        post.requireOpen(LocalDateTime.now());
        application.accept();
        post.incrementPassengers();
        carpoolMetrics.incrementApplicationAccepted();
        notificationService.save(Notification.ofApplicationAccepted(application.getApplicantId(), post.getId()));
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse reject(Long applicationId, Long requesterId) {
        Post post = lockPost(applicationId);
        requireOwner(post, requesterId);
        post.requireBeforeCutoff(LocalDateTime.now());
        Application application = findApplication(applicationId);
        requireStatus(application, ApplicationStatus.PENDING, ErrorCode.APPLICATION_ALREADY_PROCESSED);
        application.reject();
        carpoolMetrics.incrementApplicationRejected();
        notificationService.save(Notification.ofApplicationRejected(application.getApplicantId(), post.getId()));
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse cancelAccept(Long applicationId, Long requesterId) {
        Post post = lockPost(applicationId);
        requireOwner(post, requesterId);
        post.requireBeforeCutoff(LocalDateTime.now());
        Application application = findApplication(applicationId);
        requireStatus(application, ApplicationStatus.ACCEPTED, ErrorCode.APPLICATION_NOT_ACCEPTED);
        application.resetToPending();
        post.decrementPassengers();
        events.publishEvent(new ParticipantRemoved(post.getId(), application.getApplicantId()));
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse cancelReject(Long applicationId, Long requesterId) {
        Post post = lockPost(applicationId);
        requireOwner(post, requesterId);
        post.requireBeforeCutoff(LocalDateTime.now());
        Application application = findApplication(applicationId);
        requireStatus(application, ApplicationStatus.REJECTED, ErrorCode.APPLICATION_NOT_REJECTED);
        application.resetToPending();
        return toResponse(application);
    }

    @Transactional
    public ApplicationResponse cancel(Long applicationId, Long requesterId) {
        Post post = lockPost(applicationId);
        post.requireBeforeCutoff(LocalDateTime.now());
        Application application = findApplication(applicationId);
        if (!application.getApplicantId().equals(requesterId)) throw new CarpoolException(ErrorCode.APPLICATION_FORBIDDEN);
        if (application.getStatus() == ApplicationStatus.CANCELLED) return toResponse(application);
        if (application.getStatus() == ApplicationStatus.ACCEPTED) {
            post.decrementPassengers();
            events.publishEvent(new ParticipantRemoved(post.getId(), application.getApplicantId()));
        }
        application.cancel();
        return toResponse(application);
    }

    private Post lockPost(Long applicationId) {
        // Only the immutable foreign key is read first; the mutable application is loaded after the row lock.
        Long postId = applicationRepository.findPostIdById(applicationId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.APPLICATION_NOT_FOUND));
        return postRepository.findByIdAndDeletedFalseWithLock(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
    }

    private Application findApplication(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> new CarpoolException(ErrorCode.APPLICATION_NOT_FOUND));
    }
    private void requireOwner(Post post, Long requesterId) {
        if (!post.getMemberId().equals(requesterId)) throw new CarpoolException(ErrorCode.APPLICATION_FORBIDDEN);
    }
    private void requireStatus(Application application, ApplicationStatus status, ErrorCode error) {
        if (application.getStatus() != status) throw new CarpoolException(error);
    }
    private ApplicationResponse toResponse(Application application) {
        String nickname = memberRepository.findById(application.getApplicantId()).map(Member::getNickname).orElse("알 수 없음");
        return ApplicationResponse.of(application, nickname);
    }
}
