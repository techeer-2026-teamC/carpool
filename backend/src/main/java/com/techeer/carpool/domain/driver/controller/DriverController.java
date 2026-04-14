package com.techeer.carpool.domain.driver.controller;

import com.techeer.carpool.domain.driver.dto.DriverRegisterRequest;
import com.techeer.carpool.domain.driver.dto.DriverResponse;
import com.techeer.carpool.domain.driver.service.DriverRegisterService;
import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverRegisterService driverRegisterService;

    @PostMapping
    public ResponseEntity<ApiResponse<DriverResponse>> registerDriver(
            @Valid @RequestBody DriverRegisterRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        DriverResponse response = driverRegisterService.registerDriver(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("운전자 등록이 완료되었습니다.", response));
    }
}
