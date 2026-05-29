package com.techeer.carpool.domain.ride.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PassengerLocationBroadcast {
    private Long rideId;
    private Long passengerId;
    private Double latitude;
    private Double longitude;
    private String timestamp;
}
