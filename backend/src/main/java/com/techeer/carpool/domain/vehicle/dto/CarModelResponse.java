package com.techeer.carpool.domain.vehicle.dto;

import com.techeer.carpool.domain.vehicle.entity.CarModel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CarModelResponse {

    private Long id;
    private String brand;
    private String name;

    public static CarModelResponse from(CarModel carModel) {
        return CarModelResponse.builder()
                .id(carModel.getId())
                .brand(carModel.getBrand())
                .name(carModel.getName())
                .build();
    }
}