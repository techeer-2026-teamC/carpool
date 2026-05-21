package com.techeer.carpool.domain.driver.dto;

import com.techeer.carpool.domain.driver.entity.CarColor;
import com.techeer.carpool.domain.driver.entity.Driver;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DriverResponse {

    private Long driverId;
    private Long memberId;
    private String carModel;
    private CarColor carColor;
    private String carColorLabel;
    private String carNumber;
    private double averageRating;
    private LocalDateTime createdAt;

    public static DriverResponse from(Driver driver) {
        return DriverResponse.builder()
                .driverId(driver.getDriverId())
                .memberId(driver.getMemberId())
                .carModel(driver.getCarModel())
                .carColor(driver.getCarColor())
                .carColorLabel(driver.getCarColor() != null ? driver.getCarColor().getLabel() : null)
                .carNumber(driver.getCarNumber())
                .averageRating(driver.getAverageRating())
                .createdAt(driver.getCreatedAt())
                .build();
    }
}
