package com.techeer.carpool.domain.member.service;

import com.techeer.carpool.domain.member.dto.TokenResponse;
import com.techeer.carpool.domain.member.entity.RefreshToken;
import com.techeer.carpool.domain.member.repository.RefreshTokenRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenReissueService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenResponse reissue(String refreshTokenValue) {
        if (!jwtTokenProvider.validateToken(refreshTokenValue)) {
            throw new CarpoolException(ErrorCode.INVALID_TOKEN);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new CarpoolException(ErrorCode.INVALID_TOKEN));

        Long memberId = jwtTokenProvider.getMemberIdFromToken(refreshTokenValue);
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(memberId);

        refreshToken.rotate(newRefreshToken, jwtTokenProvider.getRefreshTokenExpiresAt());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
