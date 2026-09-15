package com.techeer.carpool.domain.post.dto;

import com.techeer.carpool.domain.post.entity.PostType;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class PostCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotBlank(message = "출발지는 필수입니다.")
    private String departureLocation;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double departureLat;
    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double departureLng;

    @NotBlank(message = "목적지는 필수입니다.")
    private String destinationLocation;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double destinationLat;
    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double destinationLng;

    @NotNull(message = "출발 시간은 필수입니다.")
    @Future(message = "출발 시간은 현재 이후여야 합니다.")
    private LocalDateTime departureTime;

    @Min(value = 1, message = "최대 탑승 인원은 1명 이상이어야 합니다.")
    private int maxPassengers;

    private String description;
    @AssertFalse(message = "모집자 승인 방식만 지원합니다.")
    private boolean autoAccept;
    @NotNull
    private PostType type = PostType.CARPOOL;
    @Min(0)
    private Integer price;
    private List<Long> tagIds;
}
