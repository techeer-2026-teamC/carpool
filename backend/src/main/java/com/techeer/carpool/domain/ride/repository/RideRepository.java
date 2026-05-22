package com.techeer.carpool.domain.ride.repository;

import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findAllByDriverIdOrderByCreatedAtDesc(Long driverId);

    @Query("SELECT r FROM Ride r JOIN FETCH r.passengers WHERE r.driverId = :driverId AND r.status = :status ORDER BY r.completedAt DESC")
    List<Ride> findAllByDriverIdAndStatusOrderByCompletedAtDesc(@Param("driverId") Long driverId, @Param("status") RideStatus status);

    @Query("SELECT r FROM Ride r JOIN FETCH r.passengers WHERE r.id = :rideId")
    Optional<Ride> findByIdWithPassengers(@Param("rideId") Long rideId);

    Optional<Ride> findFirstByStatus(RideStatus status);
}