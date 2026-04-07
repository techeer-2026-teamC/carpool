package com.techeer.carpool.domain.ride.repository;

import com.techeer.carpool.domain.ride.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository를 상속받으면 기본 CRUD 메서드가 자동으로 생성됨
// <Ride, Long> → 엔티티 타입이 Ride이고, PK 타입이 Long
// 별도로 메서드를 추가하지 않아도 save(), findById(), findAll(), deleteById() 등 기본 제공
public interface RideRepository extends JpaRepository<Ride, Long> {
}