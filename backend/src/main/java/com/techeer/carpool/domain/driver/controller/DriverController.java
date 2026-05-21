package com.techeer.carpool.domain.driver.controller;

import com.techeer.carpool.domain.driver.dto.DriverRegisterRequest;
import com.techeer.carpool.domain.driver.dto.DriverResponse;
import com.techeer.carpool.domain.driver.dto.DriverUpdateRequest;
import com.techeer.carpool.domain.driver.service.DriverService;
import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping
    public ResponseEntity<ApiResponse<DriverResponse>> registerDriver(
            @Valid @RequestBody DriverRegisterRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("운전자 등록이 완료되었습니다.", driverService.register(memberId, request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<DriverResponse>> getMyDriver(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("운전자 정보 조회 성공", driverService.getMyDriver(memberId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<DriverResponse>> updateDriver(
            @Valid @RequestBody DriverUpdateRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("운전자 정보가 수정되었습니다.", driverService.update(memberId, request)));
    }
}
