package com.techeer.carpool.domain.ride.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.ride.dto.RideLocationEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RideLocationRedisRepository {

    private static final String KEY_PREFIX = "ride:route:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void push(Long rideId, Double latitude, Double longitude) {
        String key = key(rideId);
        String value = serialize(new RideLocationEntry(latitude, longitude, LocalDateTime.now()));
        stringRedisTemplate.opsForList().rightPush(key, value);
        stringRedisTemplate.expire(key, TTL);
    }

    public RideLocationEntry getLatest(Long rideId) {
        String value = stringRedisTemplate.opsForList().index(key(rideId), -1);
        if (value == null) return null;
        return deserialize(value);
    }

    public List<RideLocationEntry> getAll(Long rideId) {
        List<String> values = stringRedisTemplate.opsForList().range(key(rideId), 0, -1);
        if (values == null) return Collections.emptyList();
        return values.stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void delete(Long rideId) {
        stringRedisTemplate.delete(key(rideId));
    }

    private String key(Long rideId) {
        return KEY_PREFIX + rideId;
    }

    private String serialize(RideLocationEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("RideLocationEntry 직렬화 실패: " + e.getMessage(), e);
        }
    }

    private RideLocationEntry deserialize(String value) {
        try {
            return objectMapper.readValue(value, RideLocationEntry.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
