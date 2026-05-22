package com.techeer.carpool.domain.ride.controller;

import com.techeer.carpool.domain.ride.dto.*;
import com.techeer.carpool.domain.ride.service.RideHistoryService;
import com.techeer.carpool.domain.ride.service.RideService;
import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;
    private final RideHistoryService rideHistoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<RideResponse>> createRide(
            @RequestBody @Valid RideCreateRequest request,
            Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("운행이 생성되었습니다.", rideService.createRide(request, driverId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getMyRidesAsDriver(Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("내 운행 목록 조회 성공", rideService.getMyRidesAsDriver(driverId)));
    }

    @GetMapping("/me/history")
    public ResponseEntity<ApiResponse<List<RideHistoryResponse>>> getMyHistory(Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("운행 이력 조회 성공", rideHistoryService.getMyHistory(driverId)));
    }

    @GetMapping("/me/passenger")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getMyRidesAsPassenger(Authentication authentication) {
        Long passengerId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("내 탑승 내역 조회 성공", rideService.getMyRidesAsPassenger(passengerId)));
    }

    @GetMapping("/{rideId}/history")
    public ResponseEntity<ApiResponse<RideDetailHistoryResponse>> getHistoryDetail(
            @PathVariable Long rideId,
            Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("운행 상세 기록 조회 성공", rideHistoryService.getHistoryDetail(rideId, driverId)));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<RideResponse>> getRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(ApiResponse.of("운행 조회 성공", rideService.getRide(rideId)));
    }

    @PostMapping("/{rideId}/start")
    public ResponseEntity<ApiResponse<RideResponse>> startRide(
            @PathVariable Long rideId,
            Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("운행이 시작되었습니다.", rideService.startRide(rideId, driverId)));
    }

    @PostMapping("/{rideId}/complete")
    public ResponseEntity<ApiResponse<RideResponse>> completeRide(
            @PathVariable Long rideId,
            Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("운행이 종료되었습니다.", rideService.completeRide(rideId, driverId)));
    }

    @GetMapping("/{rideId}/location")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocation(@PathVariable Long rideId) {
        return ResponseEntity.ok(ApiResponse.of("드라이버 위치 조회 성공", rideService.getLocation(rideId)));
    }

    @GetMapping("/{rideId}/passengers")
    public ResponseEntity<ApiResponse<List<PassengerResponse>>> getPassengers(@PathVariable Long rideId) {
        return ResponseEntity.ok(ApiResponse.of("탑승자 목록 조회 성공", rideService.getPassengers(rideId)));
    }

    @PostMapping("/{rideId}/passengers/{applicationId}/board")
    public ResponseEntity<ApiResponse<PassengerResponse>> boardPassenger(
            @PathVariable Long rideId,
            @PathVariable Long applicationId,
            Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("탑승이 확인되었습니다.", rideService.boardPassenger(rideId, applicationId, driverId)));
    }

    @PostMapping("/{rideId}/passengers/{applicationId}/dropoff")
    public ResponseEntity<ApiResponse<PassengerResponse>> dropOffPassenger(
            @PathVariable Long rideId,
            @PathVariable Long applicationId,
            Authentication authentication) {
        Long driverId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("하차가 확인되었습니다.", rideService.dropOffPassenger(rideId, applicationId, driverId)));
    }

}
