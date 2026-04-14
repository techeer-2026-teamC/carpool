package com.techeer.carpool.domain.vehicle.controller;

import com.techeer.carpool.domain.vehicle.dto.VehicleOptionsResponse;
import com.techeer.carpool.domain.vehicle.service.VehicleReadService;
import com.techeer.carpool.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleReadService vehicleReadService;

    @GetMapping
    public ResponseEntity<ApiResponse<VehicleOptionsResponse>> getVehicleOptions() {
        return ResponseEntity.ok(ApiResponse.of("차량 옵션 목록 조회 성공", vehicleReadService.getAllVehicleOptions()));
    }
}
