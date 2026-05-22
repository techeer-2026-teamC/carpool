package com.techeer.carpool.domain.ride.repository;

import com.techeer.carpool.domain.ride.entity.RideLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RideLocationRepository extends JpaRepository<RideLocation, Long> {

    Optional<RideLocation> findTopByRideIdOrderByRecordedAtDesc(Long rideId);
}
