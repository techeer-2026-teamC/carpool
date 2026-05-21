package com.techeer.carpool.domain.member.dto;

import com.techeer.carpool.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProfileResponse {

    private Long id;
    private String email;
    private String nickname;
    private LocalDateTime createdAt;

    public static ProfileResponse from(Member member) {
        return ProfileResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
