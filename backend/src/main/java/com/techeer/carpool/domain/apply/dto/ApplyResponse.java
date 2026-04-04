package com.techeer.carpool.domain.apply.dto;

import com.techeer.carpool.domain.apply.entity.Apply;
import com.techeer.carpool.domain.apply.entity.ApplyStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApplyResponse {

    private Long id;
    private Long postId;
    private Long memberId;
    private ApplyStatus status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ApplyResponse from(Apply apply) {
        return ApplyResponse.builder()
                .id(apply.getId())
                .postId(apply.getPostId())
                .memberId(apply.getMemberId())
                .status(apply.getStatus())
                .rejectReason(apply.getRejectReason())
                .createdAt(apply.getCreatedAt())
                .updatedAt(apply.getUpdatedAt())
                .build();
    }
}