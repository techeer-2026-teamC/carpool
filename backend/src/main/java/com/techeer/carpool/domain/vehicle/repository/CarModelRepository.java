package com.techeer.carpool.domain.vehicle.repository;

import com.techeer.carpool.domain.vehicle.entity.CarModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarModelRepository extends JpaRepository<CarModel, Long> {
}