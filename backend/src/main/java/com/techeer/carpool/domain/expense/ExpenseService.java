package com.techeer.carpool.domain.expense;

import com.techeer.carpool.domain.meeting.MeetingService;
import com.techeer.carpool.domain.meeting.MeetingView;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostType;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ExpenseService {
    private final MeetingService meetings;
    private final JdbcTemplate jdbc;
    public record Share(Long memberId, String nickname, int amount, boolean collected, boolean host) {}
    public record View(Long postId, Integer total, List<Share> shares) {}
    public record Audit(Long id, Long actorId, String action, String detail, LocalDateTime createdAt) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public View get(Long postId, Long requester) {
        MeetingView meeting = meetings.get(postId,requester);
        Post post = meetings.find(postId);
        requireTaxi(post);
        List<Integer> totals = jdbc.query("SELECT total FROM expenses WHERE post_id=?", (rs,n)->rs.getInt(1), postId);
        List<Share> shares = jdbc.query("SELECT s.member_id,m.nickname,s.amount,s.collected FROM expense_shares s JOIN members m ON m.id=s.member_id WHERE s.post_id=? ORDER BY s.member_id",
                (rs,n)->new Share(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getBoolean(4),rs.getLong(1)==meeting.hostId()),postId);
        return new View(postId, totals.isEmpty()?null:totals.get(0),shares);
    }

    @Transactional
    public View setTotal(Long postId, int total, Long requester) {
        Post post = meetings.lock(postId);
        meetings.requireHost(post,requester);
        requireTaxi(post);
        if (total < 0 || post.getMeetingCompletedAt()==null) throw new CarpoolException(ErrorCode.EXPENSE_INVALID_STATUS);
        if (jdbc.queryForObject("SELECT count(*) FROM expense_shares WHERE post_id=? AND collected=true", Long.class,postId)>0)
            throw new CarpoolException(ErrorCode.EXPENSE_INVALID_STATUS);
        List<Long> participants = meetings.get(postId,requester).participants().stream()
                .filter(p->"MET".equals(p.status())).map(MeetingView.Participant::memberId).toList();
        if(participants.size()<2) throw new CarpoolException(ErrorCode.EXPENSE_INVALID_STATUS);
        Integer previous = get(postId,requester).total();
        int changed=jdbc.update("UPDATE expenses SET total=?,updated_at=? WHERE post_id=?",total,LocalDateTime.now(),postId);
        if(changed==0) jdbc.update("INSERT INTO expenses(post_id,total,updated_at) VALUES(?,?,?)",postId,total,LocalDateTime.now());
        jdbc.update("DELETE FROM expense_shares WHERE post_id=?",postId);
        int each=total/participants.size();
        int remainder=total%participants.size();
        for(Long id:participants) jdbc.update("INSERT INTO expense_shares(post_id,member_id,amount,collected) VALUES(?,?,?,false)",
                postId,id,each+(id.equals(requester)?remainder:0));
        audit(postId,requester,"TOTAL_CHANGED",String.valueOf(previous)+" -> "+total);
        return get(postId,requester);
    }

    @Transactional
    public View collect(Long postId, Long memberId, boolean collected, Long requester) {
        Post post=meetings.lock(postId);
        meetings.requireHost(post,requester);
        requireTaxi(post);
        if(memberId.equals(requester)) throw new CarpoolException(ErrorCode.EXPENSE_INVALID_STATUS);
        int changed=jdbc.update("UPDATE expense_shares SET collected=? WHERE post_id=? AND member_id=? AND collected<>?",
                collected,postId,memberId,collected);
        if(changed==0 && jdbc.queryForObject("SELECT count(*) FROM expense_shares WHERE post_id=? AND member_id=?",Long.class,postId,memberId)==0)
            throw new CarpoolException(ErrorCode.EXPENSE_INVALID_STATUS);
        if(changed>0) audit(postId,requester,collected?"COLLECTED":"COLLECTION_UNDONE","member="+memberId);
        return get(postId,requester);
    }

    public List<Audit> history(Long postId,Long requester) {
        meetings.get(postId,requester);
        return jdbc.query("SELECT id,actor_id,action,detail,created_at FROM expense_audit WHERE post_id=? ORDER BY id DESC LIMIT 100",
                (rs,n)->new Audit(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toLocalDateTime()),postId);
    }
    private void requireTaxi(Post post) {
        if(post.getType()!=PostType.TAXI) throw new CarpoolException(ErrorCode.EXPENSE_INVALID_STATUS);
    }
    private void audit(Long postId,Long actor,String action,String detail) {
        jdbc.update("INSERT INTO expense_audit(post_id,actor_id,action,detail,created_at) VALUES(?,?,?,?,?)",postId,actor,action,detail,LocalDateTime.now());
    }
}
