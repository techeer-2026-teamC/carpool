package com.techeer.carpool.domain.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class DriverRegisterRequest {

    @NotNull(message = "차량 모델을 선택해주세요.")
    private Long carModelId;

    @NotNull(message = "차량 색상을 선택해주세요.")
    private Long carColorId;

    @NotBlank(message = "차량 번호를 입력해주세요.")
    private String carNumber;
}
