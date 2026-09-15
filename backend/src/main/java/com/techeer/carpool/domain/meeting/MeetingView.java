package com.techeer.carpool.domain.meeting;

import java.time.LocalDateTime;
import java.util.List;

public record MeetingView(Long postId, Long hostId, LocalDateTime completedAt,
                          LocalDateTime locationSharingFrom, LocalDateTime locationSharingUntil,
                          boolean locationSharingAvailable, List<Participant> participants) {
    public record Participant(Long memberId, String nickname, boolean host, String status) {}
}
