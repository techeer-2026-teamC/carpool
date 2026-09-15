package com.techeer.carpool.domain.notification.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxDeliveryStore {
    private final JdbcTemplate jdbc;

    public record Delivery(long id, long receiverId, String payload, String claimToken, int attempts,
                           LocalDateTime createdAt) { }
    public record Backlog(long pending, long retrying, double oldestSeconds) { }

    // This short transaction ends before any Redis call. Lease expiry permits recovery after a crash.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Delivery> claim(long leaseMillis) {
        String token = UUID.randomUUID().toString();
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM notification_outbox
                    WHERE published_at IS NULL AND available_at <= CURRENT_TIMESTAMP
                      AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY available_at, id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE notification_outbox o
                SET claim_token = ?, lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                    attempts = LEAST(o.attempts, 2147483646) + 1
                FROM candidate c WHERE o.id = c.id
                RETURNING o.id, o.receiver_id, o.payload, o.claim_token, o.attempts, o.created_at
                """, (rs, row) -> new Delivery(rs.getLong("id"), rs.getLong("receiver_id"),
                rs.getString("payload"), rs.getString("claim_token"), rs.getInt("attempts"),
                rs.getTimestamp("created_at").toLocalDateTime()), token, leaseMillis).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(Delivery delivery) {
        return jdbc.update("""
                UPDATE notification_outbox SET published_at = CURRENT_TIMESTAMP, lease_until = NULL, claim_token = NULL
                WHERE id = ? AND claim_token = ? AND published_at IS NULL
                """, delivery.id(), delivery.claimToken()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(Delivery delivery, long backoffMillis) {
        jdbc.update("""
                UPDATE notification_outbox
                SET available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                    lease_until = NULL, claim_token = NULL
                WHERE id = ? AND claim_token = ? AND published_at IS NULL
                """, backoffMillis, delivery.id(), delivery.claimToken());
    }

    @Transactional(readOnly = true)
    public Backlog backlog() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS pending, COUNT(*) FILTER (WHERE attempts > 0) AS retrying,
                    COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - MIN(created_at)), 0) AS oldest
                FROM notification_outbox WHERE published_at IS NULL
                """, (rs, row) -> new Backlog(rs.getLong("pending"), rs.getLong("retrying"),
                Math.max(0, rs.getDouble("oldest"))));
    }

    @Transactional
    public int removePublished(long retentionDays) {
        return jdbc.update("""
                DELETE FROM notification_outbox WHERE id IN (
                    SELECT id FROM notification_outbox
                    WHERE published_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                    ORDER BY published_at LIMIT 1000
                )
                """, retentionDays);
    }
}
