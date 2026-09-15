package com.techeer.carpool.domain.meeting;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Persistent attendance is separate from recruitment acceptance and legacy rides. */
@Entity
@Table(name="meeting_attendance")
@IdClass(MeetingAttendance.Key.class)
@NoArgsConstructor(access=AccessLevel.PROTECTED)
class MeetingAttendance {
    @Id private Long postId;
    @Id private Long memberId;
    @Column(nullable=false, length=20) private String status;
    @Column(nullable=false) private LocalDateTime updatedAt;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private Long postId;
        private Long memberId;
    }
}
