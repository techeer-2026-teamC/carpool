package com.techeer.carpool.domain.notification.type;

public enum NotificationType {
    APPLICATION_RECEIVED,  // 내 게시글에 신청 → 게시글 작성자
    APPLICATION_ACCEPTED,  // 신청 승인 → 신청자
    APPLICATION_REJECTED,  // 신청 거절 → 신청자
    RIDE_STARTED,          // 운행 시작 → 탑승 확정자 전원
    RIDE_ENDED,            // 운행 종료 → 탑승 확정자 전원
    POST_CANCELLED,        // 게시글 삭제 → ACCEPTED 신청자 전원
    DEPARTURE_APPROACHING  // 출발 1시간 전 임박 → 드라이버
}
