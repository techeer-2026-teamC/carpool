package com.techeer.carpool.domain.driver.dto;

import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.vehicle.entity.VehicleOption;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DriverResponse {

    private Long driverId;
    private Long memberId;
    private String carModelBrand;
    private String carModelName;
    private String carColorName;
    private String carColorHexCode;
    private String carNumber;
    private LocalDateTime createdAt;

    public static DriverResponse from(Driver driver, VehicleOption carModel, VehicleOption carColor) {
        return DriverResponse.builder()
                .driverId(driver.getDriverId())
                .memberId(driver.getMemberId())
                .carModelBrand(carModel.getBrand())
                .carModelName(carModel.getName())
                .carColorName(carColor.getName())
                .carColorHexCode(carColor.getHexCode())
                .carNumber(driver.getCarNumber())
                .createdAt(driver.getCreatedAt())
                .build();
    }
}
