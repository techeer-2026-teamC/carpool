package com.techeer.carpool.domain.ride.dto;

import com.techeer.carpool.domain.ride.entity.RideLocation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LocationResponse {

    private Long rideId;
    private Double driverLatitude;
    private Double driverLongitude;
    private LocalDateTime recordedAt;

    public static LocationResponse from(Long rideId, RideLocation location) {
        if (location == null) {
            return LocationResponse.builder().rideId(rideId).build();
        }
        return LocationResponse.builder()
                .rideId(rideId)
                .driverLatitude(location.getLatitude())
                .driverLongitude(location.getLongitude())
                .recordedAt(location.getRecordedAt())
                .build();
    }

    public static LocationResponse fromEntry(Long rideId, RideLocationEntry entry) {
        return LocationResponse.builder()
                .rideId(rideId)
                .driverLatitude(entry.getLatitude())
                .driverLongitude(entry.getLongitude())
                .recordedAt(entry.getRecordedAt())
                .build();
    }
}
