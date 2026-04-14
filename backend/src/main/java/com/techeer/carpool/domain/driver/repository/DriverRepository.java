package com.techeer.carpool.domain.driver.repository;

import com.techeer.carpool.domain.driver.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByMemberIdAndDeletedFalse(Long memberId);

    boolean existsByCarNumber(String carNumber);
}
