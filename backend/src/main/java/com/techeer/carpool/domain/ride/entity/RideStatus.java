package com.techeer.carpool.domain.ride.entity;

// 운행의 현재 상태를 나타내는 열거형(Enum)
// DB에는 문자열로 저장됨 (Ride.java의 @Enumerated(EnumType.STRING) 참고)
public enum RideStatus {
    SCHEDULED,      // 예정: 운행이 아직 시작되지 않은 상태 (기본값)
    IN_PROGRESS,    // 운행 중: 드라이버가 운행 시작 버튼을 누른 상태
    COMPLETED       // 완료: 운행이 종료된 상태
}