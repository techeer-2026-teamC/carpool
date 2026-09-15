package com.techeer.carpool.domain.application.controller;

import com.techeer.carpool.domain.application.dto.ApplicationResponse;
import com.techeer.carpool.domain.application.service.ApplicationCreateService;
import com.techeer.carpool.domain.application.service.ApplicationReadService;
import com.techeer.carpool.domain.application.service.ApplicationStatusService;
import com.techeer.carpool.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationCreateService applicationCreateService;
    private final ApplicationReadService applicationReadService;
    private final ApplicationStatusService applicationStatusService;

    @PostMapping("/posts/{postId}/applications")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @PathVariable Long postId,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("카풀 신청이 완료되었습니다.", applicationCreateService.apply(postId, memberId)));
    }

    @GetMapping("/applications/me")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getMyApplications(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("내 신청 내역 조회 성공", applicationReadService.getMyApplications(memberId)));
    }

    @GetMapping("/posts/{postId}/applications")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getApplicationsByPost(
            @PathVariable Long postId,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("신청 목록 조회 성공", applicationReadService.getApplicationsByPost(postId, memberId)));
    }

    @PatchMapping("/applications/{id}/accept")
    public ResponseEntity<ApiResponse<ApplicationResponse>> accept(
            @PathVariable Long id,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("신청을 수락했습니다.", applicationStatusService.accept(id, memberId)));
    }

    @PatchMapping("/applications/{id}/reject")
    public ResponseEntity<ApiResponse<ApplicationResponse>> reject(
            @PathVariable Long id,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("신청을 거절했습니다.", applicationStatusService.reject(id, memberId)));
    }

    @PatchMapping("/applications/{id}/cancel")
    public ResponseEntity<ApiResponse<ApplicationResponse>> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.of("신청을 취소했습니다.",
                applicationStatusService.cancel(id, (Long) authentication.getPrincipal())));
    }

    @PatchMapping("/applications/{id}/cancel-accept")
    public ResponseEntity<ApiResponse<ApplicationResponse>> cancelAccept(
            @PathVariable Long id,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("수락을 취소했습니다.", applicationStatusService.cancelAccept(id, memberId)));
    }

    @PatchMapping("/applications/{id}/cancel-reject")
    public ResponseEntity<ApiResponse<ApplicationResponse>> cancelReject(
            @PathVariable Long id,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("거절을 취소했습니다.", applicationStatusService.cancelReject(id, memberId)));
    }
}
