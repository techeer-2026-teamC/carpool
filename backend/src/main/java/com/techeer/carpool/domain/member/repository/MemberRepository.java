package com.techeer.carpool.domain.member.repository;

import com.techeer.carpool.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByIdAndDeletedFalse(Long id);

    boolean existsByEmail(String email);
}
