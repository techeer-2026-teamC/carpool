package com.techeer.carpool.domain.discovery.dto;

import com.techeer.carpool.domain.post.entity.PostType;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import java.time.LocalDateTime;

public record DiscoveryRequest(PostType type, LocalDateTime from, LocalDateTime to,
        Double originLat, Double originLng, Double originRadiusMeters,
        Double destinationLat, Double destinationLng, Double destinationRadiusMeters,
        int limit, String cursor) {
    public void validate() {
        if (limit < 1 || limit > 50 || !from.isBefore(to) || to.isAfter(from.plusDays(7))) invalid();
        validateCircle(originLat, originLng, originRadiusMeters);
        validateCircle(destinationLat, destinationLng, destinationRadiusMeters);
    }
    private static void validateCircle(Double lat, Double lng, Double radius) {
        if (lat == null && lng == null && radius == null) return;
        if (lat == null || lng == null || radius == null || !Double.isFinite(lat) || !Double.isFinite(lng)
                || !Double.isFinite(radius) || lat < -90 || lat > 90 || lng < -180 || lng > 180
                || radius < 1 || radius > 50_000) invalid();
    }
    private static void invalid() { throw new CarpoolException(ErrorCode.INVALID_INPUT); }
}
