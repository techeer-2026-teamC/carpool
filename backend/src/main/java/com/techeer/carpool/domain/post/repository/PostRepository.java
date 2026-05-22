package com.techeer.carpool.domain.post.repository;

import com.techeer.carpool.domain.post.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.tags WHERE p.deleted = false AND p.status = 'OPEN' AND p.departureTime >= CURRENT_TIMESTAMP ORDER BY p.departureTime ASC")
    List<Post> findByDeletedFalseWithTagsOrderByCreatedAtDesc();

    // OPEN 상태 + 미래 출발만 조회 (status, departure_time 복합 인덱스 활용)
    @Query(value = "SELECT p FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' AND p.departureTime >= CURRENT_TIMESTAMP ORDER BY p.departureTime ASC",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' AND p.departureTime >= CURRENT_TIMESTAMP")
    Page<Post> findPageByDeletedFalse(Pageable pageable);

    // 특정 ID 목록의 tags 배치 로드
    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.tags WHERE p.id IN :ids")
    List<Post> findByIdsWithTags(@Param("ids") List<Long> ids);

    Optional<Post> findByIdAndDeletedFalse(Long id);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.tags WHERE p.id = :id AND p.deleted = false")
    Optional<Post> findByIdAndDeletedFalseWithTags(@Param("id") Long id);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deleted = false")
    Optional<Post> findByIdAndDeletedFalseWithLock(@Param("id") Long id);

    List<Post> findByMemberIdAndDeletedFalse(Long memberId);
}