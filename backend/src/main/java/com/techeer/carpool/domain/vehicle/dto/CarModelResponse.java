package com.techeer.carpool.domain.vehicle.dto;

import com.techeer.carpool.domain.vehicle.entity.VehicleOption;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CarModelResponse {

    private Long id;
    private String brand;
    private String name;

    public static CarModelResponse from(VehicleOption option) {
        return CarModelResponse.builder()
                .id(option.getId())
                .brand(option.getBrand())
                .name(option.getName())
                .build();
    }
}
