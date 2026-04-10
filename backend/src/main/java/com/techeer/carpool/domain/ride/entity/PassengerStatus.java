package com.techeer.carpool.domain.ride.entity;

// 탑승자 개인의 탑승 상태를 나타내는 열거형(Enum)
public enum PassengerStatus {
    PENDING,        // 대기: 운행에 배정됐지만 아직 탑승하지 않은 상태 (기본값)
    BOARDED,        // 탑승: 드라이버가 탑승 확인 버튼을 누른 상태
    DROPPED_OFF     // 하차: 드라이버가 하차 확인 버튼을 누른 상태
}