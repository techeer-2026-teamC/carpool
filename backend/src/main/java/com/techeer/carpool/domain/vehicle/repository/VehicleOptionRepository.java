package com.techeer.carpool.domain.vehicle.repository;

import com.techeer.carpool.domain.vehicle.entity.VehicleOption;
import com.techeer.carpool.domain.vehicle.entity.VehicleOptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleOptionRepository extends JpaRepository<VehicleOption, Long> {

    List<VehicleOption> findAllByType(VehicleOptionType type);

    boolean existsByType(VehicleOptionType type);
}