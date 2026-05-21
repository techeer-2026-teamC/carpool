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

    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.tags WHERE p.deleted = false ORDER BY p.createdAt DESC")
    List<Post> findByDeletedFalseWithTagsOrderByCreatedAtDesc();

    // 페이지네이션용: SQL LIMIT 적용 (tags 미포함 — 컬렉션 fetch와 Pageable 혼용 시 in-memory 페이징 발생)
    @Query("SELECT p FROM Post p WHERE p.deleted = false ORDER BY p.createdAt DESC")
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