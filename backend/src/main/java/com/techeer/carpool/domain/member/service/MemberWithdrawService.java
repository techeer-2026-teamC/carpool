package com.techeer.carpool.domain.member.service;

import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class MemberWithdrawService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final BlacklistRedisRepository blacklistRedisRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PostRepository postRepository;
    private final ApplicationRepository applicationRepository;
    private final DriverRepository driverRepository;

    @Transactional
    public void withdraw(Long memberId, String accessToken) {
        Member member = memberRepository.findActiveByIdWithLock(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));

        // Only scalar IDs are loaded before locking; application state is read again under the post lock.
        TreeSet<Long> postIds = new TreeSet<>(postRepository.findUnresolvedOwnedIds(memberId));
        postIds.addAll(applicationRepository.findUnresolvedPostIds(memberId));
        for (Long postId : postIds) {
            Post post = postRepository.findByIdAndDeletedFalseWithLock(postId).orElse(null);
            if (post == null || post.getMeetingCompletedAt() != null) continue;
            Application application = applicationRepository.findByPostIdAndApplicantId(postId, memberId).orElse(null);
            boolean activeHost = post.getMemberId().equals(memberId)
                    && (LocalDateTime.now().isBefore(post.getDepartureTime()) || post.getCurrentPassengers() > 0);
            if (activeHost || (application != null && application.getStatus() == ApplicationStatus.ACCEPTED)) {
                throw new CarpoolException(ErrorCode.MEMBER_ACTIVE_RECRUITMENT);
            }
            if (application != null && application.getStatus() == ApplicationStatus.PENDING) application.cancel();
        }
        // Expired empty recruitment and completed history remain intact; accepted counters never change here.

        driverRepository.findByMemberIdAndDeletedFalse(memberId)
                .ifPresent(driver -> driver.delete());

        member.withdraw();
        refreshTokenRedisRepository.delete(memberId);

        if (accessToken != null) {
            long remaining = jwtTokenProvider.getRemainingSeconds(accessToken);
            blacklistRedisRepository.add(accessToken, remaining);
        }
    }
}
