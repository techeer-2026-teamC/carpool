package com.techeer.carpool.domain.ride.dto;

import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RideStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RideResponse {

    private Long id;
    private Long postId;
    private Long driverId;
    private RideStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private LocalDateTime departureTime;
    private String departureLocation;
    private Double departureLat;
    private Double departureLng;
    private String destinationLocation;
    private Double destinationLat;
    private Double destinationLng;

    public static RideResponse from(Ride ride) {
        return from(ride, null);
    }

    public static RideResponse from(Ride ride, Post post) {
        return RideResponse.builder()
                .id(ride.getId())
                .postId(ride.getPostId())
                .driverId(ride.getDriverId())
                .status(ride.getStatus())
                .startedAt(ride.getStartedAt())
                .completedAt(ride.getCompletedAt())
                .departureTime(post != null ? post.getDepartureTime() : null)
                .departureLocation(post != null ? post.getDepartureLocation() : null)
                .departureLat(post != null ? post.getDepartureLat() : null)
                .departureLng(post != null ? post.getDepartureLng() : null)
                .destinationLocation(post != null ? post.getDestinationLocation() : null)
                .destinationLat(post != null ? post.getDestinationLat() : null)
                .destinationLng(post != null ? post.getDestinationLng() : null)
                .build();
    }
}
