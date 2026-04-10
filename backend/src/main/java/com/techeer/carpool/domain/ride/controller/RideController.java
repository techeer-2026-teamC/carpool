package com.techeer.carpool.domain.ride.controller;

import com.techeer.carpool.domain.ride.dto.RideDto;
import com.techeer.carpool.domain.ride.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController          // @Controller + @ResponseBody: 모든 메서드의 반환값을 JSON으로 응답
@RequestMapping("/api/v1/rides")  // 이 컨트롤러의 모든 API 앞에 "/api/v1/rides" 붙음
@RequiredArgsConstructor // Lombok: final 필드 생성자 주입
public class RideController {

    private final RideService rideService;  // 비즈니스 로직은 서비스에게 위임

    // 운행 생성
    // POST /api/v1/rides
    @PostMapping
    public ResponseEntity<RideDto.RideResponse> createRide(
            @RequestBody @Valid RideDto.CreateRequest request) {
        // @RequestBody: HTTP 요청 Body의 JSON을 DTO 객체로 변환
        // @Valid: DTO에 선언된 @NotNull 등 유효성 검사 실행, 실패 시 400 에러
        return ResponseEntity.ok(rideService.createRide(request));
        // ResponseEntity.ok(): HTTP 200 응답과 함께 데이터 반환
    }

    // 운행 조회
    // GET /api/v1/rides/{rideId}
    @GetMapping("/{rideId}")
    public ResponseEntity<RideDto.RideResponse> getRide(
            @PathVariable Long rideId) {  // @PathVariable: URL 경로의 {rideId} 값을 파라미터로 받음
        return ResponseEntity.ok(rideService.getRide(rideId));
    }

    // 운행 시작 (드라이버가 "운행 시작" 버튼 클릭 시 호출)
    // POST /api/v1/rides/{rideId}/start
    @PostMapping("/{rideId}/start")
    public ResponseEntity<RideDto.RideResponse> startRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.startRide(rideId));
    }

    // 운행 종료 (드라이버가 "운행 종료" 버튼 클릭 시 호출)
    // POST /api/v1/rides/{rideId}/complete
    @PostMapping("/{rideId}/complete")
    public ResponseEntity<RideDto.RideResponse> completeRide(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.completeRide(rideId));
    }

    // 운전자 위치 업데이트 (브라우저 Geolocation API가 주기적으로 자동 호출)
    // POST /api/v1/rides/{rideId}/location
    // Body 예시: { "latitude": 37.321, "longitude": 127.123 }
    @PostMapping("/{rideId}/location")
    public ResponseEntity<RideDto.LocationResponse> updateLocation(
            @PathVariable Long rideId,
            @RequestBody @Valid RideDto.LocationUpdateRequest request) {
        return ResponseEntity.ok(rideService.updateLocation(rideId, request));
    }

    // 현재 드라이버 위치 조회 (탑승자가 드라이버 위치 확인 시 호출)
    // GET /api/v1/rides/{rideId}/location
    @GetMapping("/{rideId}/location")
    public ResponseEntity<RideDto.LocationResponse> getLocation(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.getLocation(rideId));
    }

    // 탑승자 목록 조회
    // GET /api/v1/rides/{rideId}/passengers
    @GetMapping("/{rideId}/passengers")
    public ResponseEntity<List<RideDto.PassengerResponse>> getPassengers(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.getPassengers(rideId));
    }

    // 탑승 확인 (드라이버가 특정 탑승자 태울 때 호출)
    // POST /api/v1/rides/{rideId}/passengers/{applicationId}/board
    @PostMapping("/{rideId}/passengers/{applicationId}/board")
    public ResponseEntity<RideDto.PassengerResponse> boardPassenger(
            @PathVariable Long rideId,
            @PathVariable Long applicationId) {  // 어떤 신청자를 태울지
        return ResponseEntity.ok(rideService.boardPassenger(rideId, applicationId));
    }

    // 하차 확인 (드라이버가 특정 탑승자 내려줄 때 호출)
    // POST /api/v1/rides/{rideId}/passengers/{applicationId}/dropoff
    @PostMapping("/{rideId}/passengers/{applicationId}/dropoff")
    public ResponseEntity<RideDto.PassengerResponse> dropOffPassenger(
            @PathVariable Long rideId,
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(rideService.dropOffPassenger(rideId, applicationId));
    }
}