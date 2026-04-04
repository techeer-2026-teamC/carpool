package com.techeer.carpool.domain.apply.repository;

import com.techeer.carpool.domain.apply.entity.Apply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplyRepository extends JpaRepository<Apply, Long> {

    List<Apply> findByMemberIdAndDeletedFalse(Long memberId);

    Optional<Apply> findByIdAndDeletedFalse(Long id);

    List<Apply> findByPostIdAndDeletedFalse(Long postId);

    boolean existsByPostIdAndMemberIdAndDeletedFalse(Long postId, Long memberId);
}