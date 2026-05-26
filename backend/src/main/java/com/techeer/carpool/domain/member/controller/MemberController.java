package com.techeer.carpool.domain.member.controller;

import com.techeer.carpool.domain.member.dto.ProfileResponse;
import com.techeer.carpool.domain.member.dto.ProfileUpdateRequest;
import com.techeer.carpool.domain.member.service.MemberProfileService;
import com.techeer.carpool.domain.member.service.MemberWithdrawService;
import com.techeer.carpool.global.common.ApiResponse;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberProfileService memberProfileService;
    private final MemberWithdrawService memberWithdrawService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfileById(
            @PathVariable Long id,
            Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        if (!callerId.equals(id)) {
            throw new CarpoolException(ErrorCode.MEMBER_FORBIDDEN);
        }
        ProfileResponse profile = memberProfileService.getProfile(id);
        return ResponseEntity.ok(ApiResponse.of("프로필을 조회했습니다.", profile));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        ProfileResponse profile = memberProfileService.getProfile(memberId);
        return ResponseEntity.ok(ApiResponse.of("프로필을 조회했습니다.", profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        ProfileResponse profile = memberProfileService.updateProfile(memberId, request);
        return ResponseEntity.ok(ApiResponse.of("프로필이 수정되었습니다.", profile));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            Authentication authentication,
            HttpServletRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        String token = resolveToken(request);
        memberWithdrawService.withdraw(memberId, token);
        return ResponseEntity.ok(ApiResponse.of("회원 탈퇴가 완료되었습니다."));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
