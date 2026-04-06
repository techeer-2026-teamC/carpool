package com.techeer.carpool.domain.member.service;

import com.techeer.carpool.domain.member.dto.AuthTokens;
import com.techeer.carpool.domain.member.dto.LoginRequest;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.entity.RefreshToken;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.member.repository.RefreshTokenRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberLoginService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthTokens login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new CarpoolException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        refreshTokenRepository.deleteByMemberId(member.getId());
        refreshTokenRepository.save(RefreshToken.builder()
                .memberId(member.getId())
                .token(refreshToken)
                .expiresAt(jwtTokenProvider.getRefreshTokenExpiresAt())
                .build());

        return new AuthTokens(accessToken, refreshToken);
    }
}
