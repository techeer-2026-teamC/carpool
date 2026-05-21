package com.techeer.carpool.domain.driver.dto;

import com.techeer.carpool.domain.driver.entity.CarColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class DriverRegisterRequest {

    @NotBlank(message = "차량 모델을 입력해주세요.")
    private String carModel;

    private CarColor carColor;

    @NotBlank(message = "차량 번호를 입력해주세요.")
    @Pattern(regexp = "^\\d{2,3}[가-힣]\\d{4}$", message = "차량 번호 형식이 올바르지 않습니다. (예: 12가3456, 123가4567)")
    private String carNumber;
}
