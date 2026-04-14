package com.techeer.carpool.domain.vehicle.service;

import com.techeer.carpool.domain.vehicle.dto.CarColorResponse;
import com.techeer.carpool.domain.vehicle.dto.CarModelResponse;
import com.techeer.carpool.domain.vehicle.dto.VehicleOptionsResponse;
import com.techeer.carpool.domain.vehicle.entity.VehicleOptionType;
import com.techeer.carpool.domain.vehicle.repository.VehicleOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleReadService {

    private final VehicleOptionRepository vehicleOptionRepository;

    @Transactional(readOnly = true)
    public VehicleOptionsResponse getAllVehicleOptions() {
        return VehicleOptionsResponse.builder()
                .models(vehicleOptionRepository.findAllByType(VehicleOptionType.MODEL).stream()
                        .map(CarModelResponse::from)
                        .toList())
                .colors(vehicleOptionRepository.findAllByType(VehicleOptionType.COLOR).stream()
                        .map(CarColorResponse::from)
                        .toList())
                .build();
    }
}
