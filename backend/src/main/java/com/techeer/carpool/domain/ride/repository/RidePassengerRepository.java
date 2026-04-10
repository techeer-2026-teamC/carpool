package com.techeer.carpool.domain.ride.repository;

import com.techeer.carpool.domain.ride.entity.RidePassenger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RidePassengerRepository extends JpaRepository<RidePassenger, Long> {

    // Spring Data JPA의 메서드 이름 규칙으로 자동 쿼리 생성
    // "findBy + 필드명 + And + 필드명" 형태로 작성하면 JPA가 알아서 SQL을 만들어줌
    // → SELECT * FROM ride_passengers WHERE ride_id = ? AND application_id = ?
    Optional<RidePassenger> findByRideIdAndApplicationId(Long rideId, Long applicationId);
}