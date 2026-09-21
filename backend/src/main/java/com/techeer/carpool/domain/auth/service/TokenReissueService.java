package com.techeer.carpool.domain.auth.service;

import com.techeer.carpool.domain.auth.dto.AuthTokens;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenReissueService {

    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // @Transactional 제거 — Redis는 DB 트랜잭션 불필요
    public AuthTokens reissue(String refreshTokenValue) {
        // 만료 → AUTH_005, 위변조 → AUTH_004 구분
        jwtTokenProvider.validateRefreshToken(refreshTokenValue);

        Long memberId = jwtTokenProvider.getMemberIdFromRefreshToken(refreshTokenValue);

        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId);

        // Compare and replace together: a used or deleted token cannot restore a session.
        if (!refreshTokenRedisRepository.rotate(memberId, refreshTokenValue, newRefreshToken)) {
            throw new CarpoolException(ErrorCode.INVALID_TOKEN);
        }

        return new AuthTokens(newAccessToken, newRefreshToken);
    }
}
