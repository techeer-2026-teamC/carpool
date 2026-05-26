package com.techeer.carpool.domain.driver.repository;

import com.techeer.carpool.domain.driver.entity.Driver;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByMemberIdAndDeletedFalse(Long memberId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT d FROM Driver d WHERE d.memberId = :memberId AND d.deleted = false")
    Optional<Driver> findByMemberIdAndDeletedFalseWithLock(@Param("memberId") Long memberId);

    List<Driver> findByMemberIdInAndDeletedFalse(Collection<Long> memberIds);

    boolean existsByCarNumberAndDeletedFalse(String carNumber);
}
