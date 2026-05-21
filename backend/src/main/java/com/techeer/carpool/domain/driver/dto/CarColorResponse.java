package com.techeer.carpool.domain.driver.dto;

import com.techeer.carpool.domain.driver.entity.CarColor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CarColorResponse {

    private String name;
    private String label;

    public static CarColorResponse from(CarColor carColor) {
        return CarColorResponse.builder()
                .name(carColor.name())
                .label(carColor.getLabel())
                .build();
    }
}
