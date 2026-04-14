package com.techeer.carpool.domain.post.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PostCreateRequest {

    @NotBlank(message = "제목을 입력해주세요.")
    private String title;

    @NotBlank(message = "출발지를 입력해주세요.")
    private String departureLocation;

    private Double departureLat;
    private Double departureLng;

    @NotBlank(message = "목적지를 입력해주세요.")
    private String destinationLocation;

    private Double destinationLat;
    private Double destinationLng;

    @NotNull(message = "출발 시간을 입력해주세요.")
    private LocalDateTime departureTime;

    @Min(value = 1, message = "최대 탑승 인원은 1명 이상이어야 합니다.")
    private int maxPassengers;

    private String description;
    private boolean autoAccept;
}
