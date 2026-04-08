package com.techeer.carpool.domain.vehicle.service;

import com.techeer.carpool.domain.vehicle.dto.CarColorResponse;
import com.techeer.carpool.domain.vehicle.dto.CarModelResponse;
import com.techeer.carpool.domain.vehicle.repository.CarColorRepository;
import com.techeer.carpool.domain.vehicle.repository.CarModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleReadService {

    private final CarModelRepository carModelRepository;
    private final CarColorRepository carColorRepository;

    @Transactional(readOnly = true)
    public List<CarModelResponse> getAllCarModels() {
        return carModelRepository.findAll().stream()
                .map(CarModelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CarColorResponse> getAllCarColors() {
        return carColorRepository.findAll().stream()
                .map(CarColorResponse::from)
                .toList();
    }
}