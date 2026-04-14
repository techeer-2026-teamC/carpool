package com.techeer.carpool.domain.vehicle.dto;

import com.techeer.carpool.domain.vehicle.entity.VehicleOption;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CarColorResponse {

    private Long id;
    private String name;
    private String hexCode;

    public static CarColorResponse from(VehicleOption option) {
        return CarColorResponse.builder()
                .id(option.getId())
                .name(option.getName())
                .hexCode(option.getHexCode())
                .build();
    }
}