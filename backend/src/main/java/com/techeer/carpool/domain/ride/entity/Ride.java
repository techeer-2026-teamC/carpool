package com.techeer.carpool.domain.ride.entity;

import com.techeer.carpool.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Ride extends BaseEntity {

    @Id                                                    // 이 필드가 PK(기본키)임을 선언
    @GeneratedValue(strategy = GenerationType.IDENTITY)    // PK를 DB에서 자동 증가(AUTO_INCREMENT)로 생성
    private Long id;

    @Column(nullable = false)    // DB 컬럼으로 매핑, null 불가
    private Long postId;         // 이 운행이 어떤 카풀 게시글에서 시작됐는지 (게시글 ID)

    @Column(nullable = false)
    private Long driverId;       // 이 운행을 담당하는 드라이버 ID

    @Enumerated(EnumType.STRING) // Enum 값을 DB에 숫자가 아닌 문자열로 저장 (예: "SCHEDULED")
    @Column(nullable = false)
    @Builder.Default             // @Builder 사용 시 기본값 설정 (안 쓰면 null이 됨)
    private RideStatus status = RideStatus.SCHEDULED;  // 운행 상태, 기본값은 "예정"

    private Double currentLatitude;   // 드라이버의 현재 위도
    private Double currentLongitude;  // 드라이버의 현재 경도

    private LocalDateTime startedAt;    // 운행 시작 시각
    private LocalDateTime completedAt;  // 운행 종료 시각

    @Builder.Default
    @OneToMany(mappedBy = "ride", cascade = CascadeType.ALL, orphanRemoval = true)
    // OneToMany: 운행 1개에 탑승자 여러 명 (1:N 관계)
    // mappedBy = "ride": RidePassenger 엔티티의 "ride" 필드가 관계의 주인
    // cascade = ALL: Ride 저장/삭제 시 RidePassenger도 함께 저장/삭제
    // orphanRemoval: 목록에서 제거된 RidePassenger는 DB에서도 자동 삭제
    private List<RidePassenger> passengers = new ArrayList<>();

    // ── 비즈니스 로직 ──────────────────────────────────────

    // 운행 시작 처리
    public void start() {
        // 예정 상태가 아니면 시작 불가 (예: 이미 진행 중이거나 완료된 운행)
        if (this.status != RideStatus.SCHEDULED) {
            throw new IllegalStateException("예정된 운행만 시작할 수 있습니다.");
        }
        this.status = RideStatus.IN_PROGRESS;  // 상태를 "운행 중"으로 변경
        this.startedAt = LocalDateTime.now();  // 시작 시각 기록
    }

    // 운행 종료 처리
    public void complete() {
        // 진행 중 상태가 아니면 종료 불가
        if (this.status != RideStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 운행만 종료할 수 있습니다.");
        }
        this.status = RideStatus.COMPLETED;       // 상태를 "완료"로 변경
        this.completedAt = LocalDateTime.now();   // 종료 시각 기록
    }

    // 드라이버 위치 업데이트 (브라우저 Geolocation API가 주기적으로 호출)
    public void updateLocation(Double latitude, Double longitude) {
        // 운행 중일 때만 위치 업데이트 허용
        if (this.status != RideStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 운행만 위치를 업데이트할 수 있습니다.");
        }
        this.currentLatitude = latitude;    // 위도 갱신
        this.currentLongitude = longitude;  // 경도 갱신
    }
}