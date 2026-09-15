package com.techeer.carpool.domain.meeting;

import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {
    private final PostRepository posts;
    private final ApplicationRepository applications;
    private final MemberRepository members;
    private final JdbcTemplate jdbc;

    public MeetingView get(Long postId, Long requester) {
        Post post = find(postId);
        List<Long> ids = participantIds(post);
        requireParticipant(ids, requester);
        return view(post, ids);
    }

    @Transactional
    public MeetingView mark(Long postId, Long memberId, String status, Long requester) {
        Post post = lock(postId);
        requireHost(post, requester);
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = participantIds(post);
        if (post.getMeetingCompletedAt() != null || now.isBefore(post.getDepartureTime().minusMinutes(30))
                || memberId.equals(post.getMemberId()) || !ids.contains(memberId)
                || !("MET".equals(status) || "NO_SHOW".equals(status))
                || ("NO_SHOW".equals(status) && now.isBefore(post.getDepartureTime()))) {
            throw new CarpoolException(ErrorCode.MEETING_INVALID_STATUS);
        }
        int changed = jdbc.update("UPDATE meeting_attendance SET status=?,updated_at=? WHERE post_id=? AND member_id=?",
                status, now, postId, memberId);
        if (changed == 0) jdbc.update("INSERT INTO meeting_attendance(post_id,member_id,status,updated_at) VALUES(?,?,?,?)",
                postId, memberId, status, now);
        return view(post, ids);
    }

    @Transactional
    public MeetingView complete(Long postId, Long requester) {
        Post post = lock(postId);
        requireHost(post, requester);
        List<Long> ids = participantIds(post);
        MeetingView before = view(post, ids);
        if (post.getMeetingCompletedAt() != null) return before;
        if (ids.size() < 2 || LocalDateTime.now().isBefore(post.getDepartureTime().minusMinutes(30))
                || before.participants().stream().anyMatch(p -> "PENDING".equals(p.status()))) {
            throw new CarpoolException(ErrorCode.MEETING_INVALID_STATUS);
        }
        post.completeMeeting(LocalDateTime.now());
        return view(post, ids);
    }

    @org.springframework.context.event.EventListener
    @Transactional
    public void removeAttendance(com.techeer.carpool.domain.application.event.ParticipantRemoved event) {
        jdbc.update("DELETE FROM meeting_attendance WHERE post_id=? AND member_id=?",event.postId(),event.memberId());
    }

    public Post find(Long id) {
        return posts.findByIdAndDeletedFalse(id).orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
    }
    public Post lock(Long id) {
        return posts.findByIdAndDeletedFalseWithLock(id).orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
    }
    public List<Long> participantIds(Post post) {
        List<Long> result = new ArrayList<>();
        result.add(post.getMemberId());
        applications.findByPostIdAndStatus(post.getId(), ApplicationStatus.ACCEPTED)
                .stream().map(a -> a.getApplicantId()).sorted().forEach(result::add);
        return result;
    }
    public void requireHost(Post post, Long requester) {
        if (!post.getMemberId().equals(requester)) throw new CarpoolException(ErrorCode.MEETING_FORBIDDEN);
    }
    public void requireParticipant(List<Long> ids, Long requester) {
        if (!ids.contains(requester)) throw new CarpoolException(ErrorCode.MEETING_FORBIDDEN);
    }
    private MeetingView view(Post post, List<Long> ids) {
        Map<Long,String> names = members.findAllById(ids).stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m.getNickname()));
        Map<Long,String> statuses = new HashMap<>();
        jdbc.query("SELECT member_id,status FROM meeting_attendance WHERE post_id=?", rs -> {
            statuses.put(rs.getLong("member_id"), rs.getString("status"));
        }, post.getId());
        LocalDateTime from = post.getDepartureTime().minusMinutes(30);
        LocalDateTime until = post.getDepartureTime().plusMinutes(30);
        LocalDateTime now = LocalDateTime.now();
        return new MeetingView(post.getId(), post.getMemberId(), post.getMeetingCompletedAt(), from, until,
                post.getMeetingCompletedAt() == null && !now.isBefore(from) && now.isBefore(until),
                ids.stream().map(id -> new MeetingView.Participant(id, names.getOrDefault(id, "참가자"),
                        id.equals(post.getMemberId()), id.equals(post.getMemberId()) ? "MET" : statuses.getOrDefault(id, "PENDING"))).toList());
    }
}
