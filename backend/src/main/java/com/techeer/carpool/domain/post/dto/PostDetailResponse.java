package com.techeer.carpool.domain.post.dto;

import com.techeer.carpool.domain.comment.dto.CommentResponse;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PostDetailResponse {

    private Long id;
    private Long memberId;
    private String nickname;
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
    private String description;
    private boolean autoAccept;
    private Integer price;
    private List<TagResponse> tags;
    private List<CommentResponse> comments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostDetailResponse from(Post post, String nickname, List<CommentResponse> comments) {
        return PostDetailResponse.builder()
                .id(post.getId())
                .memberId(post.getMemberId())
                .nickname(nickname)
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
                .description(post.getDescription())
                .autoAccept(post.isAutoAccept())
                .price(post.getPrice())
                .tags(post.getTags().stream().map(TagResponse::from).toList())
                .comments(comments)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
