package com.techeer.carpool.domain.ride.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ride_passengers")  // DB 테이블 이름: "ride_passengers"
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class RidePassenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    // ManyToOne: 탑승자 여러 명이 운행 1개에 속함 (N:1 관계)
    // fetch = LAZY: ride 정보가 실제로 필요할 때만 DB 조회 (성능 최적화)
    @JoinColumn(name = "ride_id", nullable = false)  // FK 컬럼 이름을 "ride_id"로 지정
    private Ride ride;

    @Column(nullable = false)
    private Long applicationId;   // 카풀 신청 ID (탑승자가 신청한 내역의 ID)

    @Column(nullable = false)
    private Long passengerId;     // 탑승자의 사용자 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default              // 빌더로 생성 시 기본값 지정
    private PassengerStatus status = PassengerStatus.PENDING;  // 기본값은 "대기"

    private LocalDateTime boardedAt;     // 탑승 확인 시각
    private LocalDateTime droppedOffAt;  // 하차 확인 시각

    // ── 비즈니스 로직 ──────────────────────────────────────

    // 탑승 확인 처리 (드라이버가 탑승자를 태웠을 때 호출)
    public void board() {
        // 대기 상태인 탑승자만 탑승 확인 가능
        if (this.status != PassengerStatus.PENDING) {
            throw new IllegalStateException("대기 중인 탑승자만 탑승 확인할 수 있습니다.");
        }
        this.status = PassengerStatus.BOARDED;   // 상태를 "탑승"으로 변경
        this.boardedAt = LocalDateTime.now();    // 탑승 시각 기록
    }

    // 하차 확인 처리 (드라이버가 탑승자를 내려줬을 때 호출)
    public void dropOff() {
        // 탑승 상태인 탑승자만 하차 확인 가능
        if (this.status != PassengerStatus.BOARDED) {
            throw new IllegalStateException("탑승 중인 탑승자만 하차 확인할 수 있습니다.");
        }
        this.status = PassengerStatus.DROPPED_OFF;   // 상태를 "하차"로 변경
        this.droppedOffAt = LocalDateTime.now();     // 하차 시각 기록
    }
}