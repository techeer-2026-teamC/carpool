package com.techeer.carpool.domain.apply.controller;

import com.techeer.carpool.domain.apply.dto.ApplyCreateRequest;
import com.techeer.carpool.domain.apply.dto.ApplyResponse;
import com.techeer.carpool.domain.apply.dto.ApplyUpdateRequest;
import com.techeer.carpool.domain.apply.service.ApplyCreateService;
import com.techeer.carpool.domain.apply.service.ApplyReadService;
import com.techeer.carpool.domain.apply.service.ApplyUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplyController {

    private final ApplyCreateService applyCreateService;
    private final ApplyReadService applyReadService;
    private final ApplyUpdateService applyUpdateService;

    // 참여 신청
    @PostMapping("/api/v1/posts/{postId}/apply")
    public ResponseEntity<ApplyResponse> createApply(
            @PathVariable Long postId,
            @RequestBody ApplyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applyCreateService.createApply(postId, request));
    }

    // 내 신청 결과 조회
    @GetMapping("/api/v1/applies/me")
    public ResponseEntity<List<ApplyResponse>> getMyApplies(@RequestParam Long memberId) {
        return ResponseEntity.ok(applyReadService.getMyApplies(memberId));
    }

    // 신청 현황
    @GetMapping("/api/v1/applies/{applyId}")
    public ResponseEntity<ApplyResponse> getApplyById(@PathVariable Long applyId) {
        return ResponseEntity.ok(applyReadService.getApplyById(applyId));
    }

    // [운전자] 신청자 목록
    @GetMapping("/api/v1/posts/{postId}/applies")
    public ResponseEntity<List<ApplyResponse>> getAppliesByPostId(@PathVariable Long postId) {
        return ResponseEntity.ok(applyReadService.getAppliesByPostId(postId));
    }

    // [운전자] 신청 수락/거절
    @PatchMapping("/api/v1/applies/{applyId}")
    public ResponseEntity<ApplyResponse> updateApply(
            @PathVariable Long applyId,
            @RequestBody ApplyUpdateRequest request) {
        return ResponseEntity.ok(applyUpdateService.updateApply(applyId, request));
    }
}