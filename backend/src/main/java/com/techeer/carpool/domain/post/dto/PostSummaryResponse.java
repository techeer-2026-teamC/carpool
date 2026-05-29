package com.techeer.carpool.domain.post.dto;

import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Builder
@NoArgsConstructor(access = PRIVATE)
@AllArgsConstructor(access = PRIVATE)
public class PostSummaryResponse {

    private Long id;
    private Long memberId;
    private String nickname;
    private double driverAverageRating;
    private String title;
    private String departureLocation;
    private Double departureLat;
    private Double departureLng;
    private String destinationLocation;
    private Double destinationLat;
    private Double destinationLng;
    private LocalDateTime departureTime;
    private int maxPassengers;
    private int currentPassengers;
    private PostStatus status;
    private boolean autoAccept;
    private Integer price;
    private List<TagResponse> tags;
    private LocalDateTime createdAt;

    public static PostSummaryResponse from(Post post, String nickname, double driverAverageRating) {
        return PostSummaryResponse.builder()
                .id(post.getId())
                .memberId(post.getMemberId())
                .nickname(nickname)
                .driverAverageRating(driverAverageRating)
                .title(post.getTitle())
                .departureLocation(post.getDepartureLocation())
                .departureLat(post.getDepartureLat())
                .departureLng(post.getDepartureLng())
                .destinationLocation(post.getDestinationLocation())
                .destinationLat(post.getDestinationLat())
                .destinationLng(post.getDestinationLng())
                .departureTime(post.getDepartureTime())
                .maxPassengers(post.getMaxPassengers())
                .currentPassengers(post.getCurrentPassengers())
                .status(post.getStatus())
                .autoAccept(post.isAutoAccept())
                .price(post.getPrice())
                .tags(post.getTags().stream().map(TagResponse::from).toList())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
