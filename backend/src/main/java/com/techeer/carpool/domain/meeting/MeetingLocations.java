package com.techeer.carpool.domain.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.techeer.carpool.domain.application.event.ParticipantRemoved;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingLocations {
    public static final String CHANNEL = "moa:meeting:positions";
    private final MeetingService meetings;
    private final MeetingLocationStore store;
    private final ObjectMapper json;
    public record Position(Long postId, Long memberId, double latitude, double longitude, String recordedAt) {}
    public record Sharing(String generation) {}

    @Transactional
    public Sharing start(Long postId, Long requester) {
        // Start and membership removal share the post lock; a cancelled participant cannot
        // recreate the Redis session between its deletion and the cancellation commit.
        meetings.lock(postId);
        MeetingView meeting = meetings.get(postId, requester);
        long ttl = Duration.between(LocalDateTime.now(), meeting.locationSharingUntil()).toMillis();
        if (!meeting.locationSharingAvailable() || ttl <= 0) throw new CarpoolException(ErrorCode.LOCATION_WINDOW_CLOSED);
        String generation = UUID.randomUUID().toString();
        store.start(postId, requester, generation, ttl);
        return new Sharing(generation);
    }

    public void update(Long postId, Long memberId, double lat, double lng, String generation) {
        requireGeneration(generation);
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat)>90 || Math.abs(lng)>180)
            throw new CarpoolException(ErrorCode.INVALID_INPUT);
        MeetingView meeting = meetings.get(postId, memberId);
        if (!meeting.locationSharingAvailable()) throw new CarpoolException(ErrorCode.LOCATION_WINDOW_CLOSED);
        Position position = new Position(postId, memberId, lat, lng, Instant.now().toString());
        try {
            String data = json.writeValueAsString(position);
            store.update(postId, memberId, generation, data);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode meeting position", e);
        }
    }

    public List<Position> get(Long postId, Long requester) {
        MeetingView meeting = meetings.get(postId, requester);
        if (!meeting.locationSharingAvailable()) return List.of();
        List<Position> result = new ArrayList<>();
        for (MeetingView.Participant p : meeting.participants()) {
            if (!requester.equals(meeting.hostId()) && !p.host() && !p.memberId().equals(requester)) continue;
            String value = store.get(postId, p.memberId());
            if (value != null) {
                try { result.add(json.readValue(value, Position.class)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException e) { log.warn("Ignoring invalid meeting position"); }
            }
        }
        return result;
    }

    public boolean isCurrent(Position position) {
        String value = store.get(position.postId(), position.memberId());
        if (value == null) return false;
        try { return position.equals(json.readValue(value, Position.class)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { return false; }
    }

    public void stop(Long postId, Long requester, String generation) {
        requireGeneration(generation);
        // The authenticated caller can only remove their own generation, including after cancellation.
        store.stop(postId, requester, generation);
    }
    @EventListener
    public void removed(ParticipantRemoved event) {
        // Synchronous under the caller's post lock. Redis failure rolls back cancellation;
        // a later DB rollback only revokes sharing and requires an explicit restart.
        store.revoke(event.postId(), event.memberId());
    }
    public void clear(MeetingView meeting) {
        try { meeting.participants().forEach(p -> store.revoke(meeting.postId(), p.memberId())); }
        catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // The completed DB state immediately denies location reads; TTL removes inaccessible values.
            log.warn("Completed meeting location deletion deferred to TTL");
        }
    }
    private void requireGeneration(String generation) {
        if (generation == null || !generation.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new CarpoolException(ErrorCode.INVALID_INPUT);
    }
}
