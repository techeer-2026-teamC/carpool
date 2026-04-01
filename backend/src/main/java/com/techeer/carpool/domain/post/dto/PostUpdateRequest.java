package com.techeer.carpool.domain.post.dto;

import com.techeer.carpool.domain.post.entity.PostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostUpdateRequest {

    private String title;
    private String departureLocation;
    private Double departureLat;
    private Double departureLng;
    private String destinationLocation;
    private Double destinationLat;
    private Double destinationLng;
    private LocalDateTime departureTime;
    private int maxPassengers;
    private String description;
    private boolean autoAccept;
    private PostStatus status;
}
