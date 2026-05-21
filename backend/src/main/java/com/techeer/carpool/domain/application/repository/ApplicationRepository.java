package com.techeer.carpool.domain.application.repository;

import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    List<Application> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    List<Application> findByPostIdOrderByCreatedAtAsc(Long postId);

    long countByPostIdAndStatus(Long postId, ApplicationStatus status);

    List<Application> findByPostIdAndStatus(Long postId, ApplicationStatus status);

    List<Application> findByApplicantIdAndStatus(Long applicantId, ApplicationStatus status);
}
