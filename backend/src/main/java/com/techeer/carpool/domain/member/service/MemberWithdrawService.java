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

import java.util.List;

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
        Member member = memberRepository.findByIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));

        List<Post> myPosts = postRepository.findByMemberIdAndDeletedFalse(memberId);
        myPosts.forEach(Post::delete);

        List<Application> acceptedApplications = applicationRepository
                .findByApplicantIdAndStatus(memberId, ApplicationStatus.ACCEPTED);
        acceptedApplications.forEach(Application::reject);

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
