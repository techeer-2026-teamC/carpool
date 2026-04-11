package com.techeer.carpool.domain.member.service;

import com.techeer.carpool.domain.member.dto.ProfileResponse;
import com.techeer.carpool.domain.member.dto.ProfileUpdateRequest;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileResponse getProfile(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));
        return ProfileResponse.from(member);
    }

    @Transactional
    public ProfileResponse updateProfile(Long memberId, ProfileUpdateRequest request) {
        Member member = memberRepository.findByIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND));

        if (request.getNickname() != null) {
            member.updateNickname(request.getNickname());
        }

        if (request.getNewPassword() != null) {
            if (request.getCurrentPassword() == null ||
                    !passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
                throw new CarpoolException(ErrorCode.INVALID_CREDENTIALS);
            }
            member.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        }

        return ProfileResponse.from(member);
    }
}
