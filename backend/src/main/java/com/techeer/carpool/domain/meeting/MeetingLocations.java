package com.techeer.carpool.domain.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingLocations {
    public static final String CHANNEL = "moa:meeting:positions";
    private final MeetingService meetings;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public record Position(Long postId, Long memberId, double latitude, double longitude, String recordedAt) {}

    public void update(Long postId, Long memberId, double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat)>90 || Math.abs(lng)>180)
            throw new CarpoolException(ErrorCode.INVALID_INPUT);
        MeetingView meeting = meetings.get(postId, memberId);
        if (!meeting.locationSharingAvailable()) throw new CarpoolException(ErrorCode.LOCATION_WINDOW_CLOSED);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(postId,memberId)+":rate", "1", Duration.ofSeconds(4)))) return;
        Position position = new Position(postId, memberId, lat, lng, Instant.now().toString());
        try {
            String data = json.writeValueAsString(position);
            redis.opsForValue().set(key(postId,memberId), data, Duration.ofSeconds(60));
            redis.convertAndSend(CHANNEL, data);
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
            String value = redis.opsForValue().get(key(postId,p.memberId()));
            if (value != null) {
                try { result.add(json.readValue(value, Position.class)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException e) { log.warn("Ignoring invalid meeting position"); }
            }
        }
        return result;
    }

    public void stop(Long postId, Long requester) {
        meetings.get(postId, requester);
        redis.delete(key(postId, requester));
    }
    public void clear(MeetingView meeting) {
        try { redis.delete(meeting.participants().stream().map(p -> key(meeting.postId(),p.memberId())).toList()); }
        catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // The completed DB state immediately denies location reads; TTL removes inaccessible values.
            log.warn("Completed meeting location deletion deferred to TTL");
        }
    }
    private String key(Long postId, Long memberId) { return "moa:meeting:"+postId+":member:"+memberId; }
}
