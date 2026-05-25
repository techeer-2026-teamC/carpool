package com.techeer.carpool.domain.ride.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RideLocationEntry {

    private final Double latitude;
    private final Double longitude;
    private final LocalDateTime recordedAt;

    @JsonCreator
    public RideLocationEntry(
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("longitude") Double longitude,
            @JsonProperty("recordedAt") LocalDateTime recordedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }
}
