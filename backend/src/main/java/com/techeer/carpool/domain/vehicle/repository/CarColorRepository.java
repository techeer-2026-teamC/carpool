package com.techeer.carpool.domain.vehicle.repository;

import com.techeer.carpool.domain.vehicle.entity.CarColor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarColorRepository extends JpaRepository<CarColor, Long> {
}