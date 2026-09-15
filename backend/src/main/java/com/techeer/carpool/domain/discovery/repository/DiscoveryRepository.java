package com.techeer.carpool.domain.discovery.repository;

import com.techeer.carpool.domain.discovery.dto.DiscoveryCursor;
import com.techeer.carpool.domain.discovery.dto.DiscoveryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DiscoveryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public List<Long> searchIds(DiscoveryRequest request, DiscoveryCursor cursor) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id FROM posts p
            WHERE p.deleted = false AND p.status = 'OPEN' AND p.meeting_completed_at IS NULL
              AND p.departure_time >= :from AND p.departure_time < :to AND p.departure_time > :now
              AND p.current_passengers + CASE WHEN p.type = 'TAXI' THEN 1 ELSE 0 END < p.max_passengers
            """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Timestamp.valueOf(request.from())).addValue("to", Timestamp.valueOf(request.to()))
                .addValue("now", Timestamp.valueOf(LocalDateTime.now())).addValue("limit", request.limit() + 1);
        if (request.type() != null) {
            sql.append(" AND p.type = :type");
            params.addValue("type", request.type().name());
        }
        if (request.originLat() != null) {
            sql.append(" AND ST_DWithin(p.origin_geography, CAST(ST_SetSRID(ST_MakePoint(:originLng, :originLat),4326) AS geography), :originRadius)");
            params.addValue("originLat", request.originLat()).addValue("originLng", request.originLng()).addValue("originRadius", request.originRadiusMeters());
        }
        if (request.destinationLat() != null) {
            sql.append(" AND ST_DWithin(p.destination_geography, CAST(ST_SetSRID(ST_MakePoint(:destinationLng, :destinationLat),4326) AS geography), :destinationRadius)");
            params.addValue("destinationLat", request.destinationLat()).addValue("destinationLng", request.destinationLng()).addValue("destinationRadius", request.destinationRadiusMeters());
        }
        if (cursor != null) {
            sql.append(" AND (p.departure_time, p.id) > (:cursorTime, :cursorId)");
            params.addValue("cursorTime", Timestamp.valueOf(cursor.departureTime())).addValue("cursorId", cursor.id());
        }
        sql.append(" ORDER BY p.departure_time, p.id LIMIT :limit");
        return jdbc.queryForList(sql.toString(), params, Long.class);
    }
}
