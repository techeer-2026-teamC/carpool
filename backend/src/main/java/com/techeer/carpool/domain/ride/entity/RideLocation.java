package com.techeer.carpool.domain.ride.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ride_locations",
        indexes = @Index(name = "idx_ride_locations_ride_id_recorded_at", columnList = "ride_id, recorded_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class RideLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ride_id", nullable = false)
    private Long rideId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public static RideLocation of(Long rideId, Double latitude, Double longitude) {
        return RideLocation.builder()
                .rideId(rideId)
                .latitude(latitude)
                .longitude(longitude)
                .recordedAt(LocalDateTime.now())
                .build();
    }
}
