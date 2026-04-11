package com.techeer.carpool.domain.member.controller;

import com.techeer.carpool.domain.member.dto.ProfileResponse;
import com.techeer.carpool.domain.member.dto.ProfileUpdateRequest;
import com.techeer.carpool.domain.member.service.MemberProfileService;
import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberProfileService memberProfileService;

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
}
