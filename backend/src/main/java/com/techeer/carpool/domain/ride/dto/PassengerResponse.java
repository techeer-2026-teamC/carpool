package com.techeer.carpool.domain.ride.dto;

import com.techeer.carpool.domain.ride.entity.PassengerStatus;
import com.techeer.carpool.domain.ride.entity.RidePassenger;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PassengerResponse {

    private Long id;
    private Long applicationId;
    private Long passengerId;
    private String nickname;
    private PassengerStatus status;
    private LocalDateTime boardedAt;
    private LocalDateTime droppedOffAt;

    public static PassengerResponse from(RidePassenger p) {
        return PassengerResponse.builder()
                .id(p.getId())
                .applicationId(p.getApplicationId())
                .passengerId(p.getPassengerId())
                .status(p.getStatus())
                .boardedAt(p.getBoardedAt())
                .droppedOffAt(p.getDroppedOffAt())
                .build();
    }

    public static PassengerResponse from(RidePassenger p, String nickname) {
        return PassengerResponse.builder()
                .id(p.getId())
                .applicationId(p.getApplicationId())
                .passengerId(p.getPassengerId())
                .nickname(nickname)
                .status(p.getStatus())
                .boardedAt(p.getBoardedAt())
                .droppedOffAt(p.getDroppedOffAt())
                .build();
    }
}
