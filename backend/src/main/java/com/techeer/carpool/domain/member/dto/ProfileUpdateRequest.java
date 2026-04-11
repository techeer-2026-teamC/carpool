package com.techeer.carpool.domain.member.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ProfileUpdateRequest {

    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    private String nickname;

    private String currentPassword;

    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String newPassword;
}
