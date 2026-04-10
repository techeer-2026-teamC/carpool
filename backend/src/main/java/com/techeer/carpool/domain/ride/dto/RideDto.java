package com.techeer.carpool.domain.ride.dto;

import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RidePassenger;
import com.techeer.carpool.domain.ride.entity.RideStatus;
import com.techeer.carpool.domain.ride.entity.PassengerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// DTO(Data Transfer Object): 클라이언트 ↔ 서버 간 데이터를 주고받는 객체
// 엔티티를 그대로 외부에 노출하지 않고, 필요한 데이터만 골라서 전달하기 위해 사용
public class RideDto {

    // ── Request (클라이언트 → 서버) ────────────────────────

    // 운행 생성 요청 시 클라이언트가 보내는 데이터
    @Getter
    public static class CreateRequest {
        @NotNull(message = "postId는 필수입니다.")  // 값이 null이면 400 에러 반환
        private Long postId;    // 어떤 게시글 기반으로 운행을 만들지

        @NotNull(message = "driverId는 필수입니다.")
        private Long driverId;  // 드라이버가 누구인지
    }

    // 위치 업데이트 요청 시 클라이언트(브라우저 Geolocation API)가 보내는 데이터
    @Getter
    public static class LocationUpdateRequest {
        @NotNull(message = "latitude는 필수입니다.")
        private Double latitude;   // 위도 (예: 37.5665)

        @NotNull(message = "longitude는 필수입니다.")
        private Double longitude;  // 경도 (예: 126.9780)
    }

    // ── Response (서버 → 클라이언트) ───────────────────────

    // 운행 생성/조회/시작/종료 시 클라이언트에 돌려주는 데이터
    @Getter
    @Builder  // 빌더 패턴으로 객체 생성 (from() 메서드 안에서 사용)
    public static class RideResponse {
        private Long id;
        private Long postId;
        private Long driverId;
        private RideStatus status;
        private Double currentLatitude;
        private Double currentLongitude;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;

        // 엔티티(Ride) → DTO(RideResponse) 변환 메서드
        // 서비스에서 ride 엔티티를 받아서 클라이언트에 줄 형태로 변환할 때 사용
        public static RideResponse from(Ride ride) {
            return RideResponse.builder()
                    .id(ride.getId())
                    .postId(ride.getPostId())
                    .driverId(ride.getDriverId())
                    .status(ride.getStatus())
                    .currentLatitude(ride.getCurrentLatitude())
                    .currentLongitude(ride.getCurrentLongitude())
                    .startedAt(ride.getStartedAt())
                    .completedAt(ride.getCompletedAt())
                    .build();
        }
    }

    // 위치 조회/업데이트 시 클라이언트에 돌려주는 데이터 (위치 정보만 담아서 가볍게)
    @Getter
    @Builder
    public static class LocationResponse {
        private Long rideId;
        private Double driverLatitude;   // 드라이버 현재 위도
        private Double driverLongitude;  // 드라이버 현재 경도

        public static LocationResponse from(Ride ride) {
            return LocationResponse.builder()
                    .rideId(ride.getId())
                    .driverLatitude(ride.getCurrentLatitude())
                    .driverLongitude(ride.getCurrentLongitude())
                    .build();
        }
    }

    // 탑승자 조회/탑승확인/하차확인 시 클라이언트에 돌려주는 데이터
    @Getter
    @Builder
    public static class PassengerResponse {
        private Long id;
        private Long applicationId;
        private Long passengerId;
        private PassengerStatus status;
        private LocalDateTime boardedAt;
        private LocalDateTime droppedOffAt;

        // 엔티티(RidePassenger) → DTO(PassengerResponse) 변환
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
    }
}