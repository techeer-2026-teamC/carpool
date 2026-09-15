package com.techeer.carpool.domain.application.service;

import com.techeer.carpool.domain.application.dto.ApplicationResponse;
import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import java.time.LocalDateTime;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationCreateService {

    private final ApplicationRepository applicationRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final CarpoolMetrics carpoolMetrics;

    @Transactional
    public ApplicationResponse apply(Long postId, Long applicantId) {
        // Lifecycle mutations always lock the member before any recruitment row.
        memberRepository.findActiveByIdWithLock(applicantId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));
        Post post = postRepository.findByIdAndDeletedFalseWithLock(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (post.getMemberId().equals(applicantId)) {
            throw new CarpoolException(ErrorCode.APPLICATION_SELF);
        }

        post.requireOpen(LocalDateTime.now());
        Application application = applicationRepository.findByPostIdAndApplicantId(postId, applicantId).orElse(null);
        if (application != null && application.getStatus() != ApplicationStatus.CANCELLED) {
            throw new CarpoolException(ErrorCode.APPLICATION_DUPLICATE);
        }
        if (application == null) application = Application.builder().postId(postId).applicantId(applicantId).build();
        else application.resetToPending();

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

        return ApplicationResponse.of(saved, nickname);
    }
}
