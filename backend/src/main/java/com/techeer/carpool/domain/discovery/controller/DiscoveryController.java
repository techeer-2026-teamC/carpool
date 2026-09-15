package com.techeer.carpool.domain.discovery.controller;

import com.techeer.carpool.domain.discovery.dto.DiscoveryPage;
import com.techeer.carpool.domain.discovery.dto.DiscoveryRequest;
import com.techeer.carpool.domain.discovery.service.DiscoveryService;
import com.techeer.carpool.domain.post.entity.PostType;
import com.techeer.carpool.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/discovery/posts")
@RequiredArgsConstructor
public class DiscoveryController {
    private final DiscoveryService discoveryService;

    @GetMapping
    public ApiResponse<DiscoveryPage> search(
            @RequestParam(required = false) PostType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Double originLat, @RequestParam(required = false) Double originLng,
            @RequestParam(required = false) Double originRadiusMeters,
            @RequestParam(required = false) Double destinationLat, @RequestParam(required = false) Double destinationLng,
            @RequestParam(required = false) Double destinationRadiusMeters,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor) {
        LocalDateTime start = from != null ? from : LocalDateTime.now();
        LocalDateTime end = to != null ? to : start.plusDays(2);
        return ApiResponse.of("모집 검색 성공", discoveryService.search(new DiscoveryRequest(type, start, end,
                originLat, originLng, originRadiusMeters, destinationLat, destinationLng, destinationRadiusMeters, limit, cursor)));
    }
}
