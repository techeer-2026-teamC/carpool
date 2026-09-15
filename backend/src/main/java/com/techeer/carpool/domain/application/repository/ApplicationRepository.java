package com.techeer.carpool.domain.application.repository;

import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByPostId(Long postId);

    @Query("select a.postId from Application a where a.id = :id")
    Optional<Long> findPostIdById(@Param("id") Long id);

    Optional<Application> findByPostIdAndApplicantId(Long postId, Long applicantId);

    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    List<Application> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    List<Application> findByPostIdOrderByCreatedAtAsc(Long postId);

    long countByPostIdAndStatus(Long postId, ApplicationStatus status);

    List<Application> findByPostIdAndStatus(Long postId, ApplicationStatus status);

    List<Application> findByApplicantIdAndStatus(Long applicantId, ApplicationStatus status);
}
