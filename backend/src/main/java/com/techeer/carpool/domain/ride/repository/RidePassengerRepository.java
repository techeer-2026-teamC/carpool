package com.techeer.carpool.domain.ride.repository;

import com.techeer.carpool.domain.ride.entity.RidePassenger;
import com.techeer.carpool.domain.ride.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RidePassengerRepository extends JpaRepository<RidePassenger, Long> {

    Optional<RidePassenger> findByRideIdAndApplicationId(Long rideId, Long applicationId);

    List<RidePassenger> findByRideId(Long rideId);

    List<RidePassenger> findAllByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    @Query("SELECT rp FROM RidePassenger rp JOIN FETCH rp.ride WHERE rp.passengerId = :passengerId ORDER BY rp.createdAt DESC")
    List<RidePassenger> findAllByPassengerIdWithRideOrderByCreatedAtDesc(@Param("passengerId") Long passengerId);

    @Query("SELECT rp FROM RidePassenger rp JOIN FETCH rp.ride WHERE rp.passengerId = :passengerId AND rp.ride.status = :status ORDER BY rp.ride.completedAt DESC")
    List<RidePassenger> findByPassengerIdAndRideStatus(@Param("passengerId") Long passengerId, @Param("status") RideStatus status);
}