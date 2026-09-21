package com.techeer.carpool.domain.auth.service;

import com.techeer.carpool.domain.auth.dto.AuthTokens;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenReissueService {

    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository members;

    @Transactional
    public AuthTokens reissue(String refreshTokenValue) {
        // 만료 → AUTH_005, 위변조 → AUTH_004 구분
        jwtTokenProvider.validateRefreshToken(refreshTokenValue);

        Long memberId = jwtTokenProvider.getMemberIdFromRefreshToken(refreshTokenValue);
        members.findActiveByIdWithLock(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.INVALID_TOKEN));

        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId);

        // Compare and replace together: a used or deleted token cannot restore a session.
        if (!refreshTokenRedisRepository.rotate(memberId, refreshTokenValue, newRefreshToken)) {
            throw new CarpoolException(ErrorCode.INVALID_TOKEN);
        }

        return new AuthTokens(newAccessToken, newRefreshToken);
    }
}
