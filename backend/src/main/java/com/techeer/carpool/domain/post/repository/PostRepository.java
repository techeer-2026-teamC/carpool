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

import java.time.LocalDateTime;

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

    // 출발 시간이 지났지만 아직 OPEN 상태인 게시글 (자동 마감 대상)
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' AND p.departureTime <= :now")
    List<Post> findExpiredOpenPosts(@Param("now") LocalDateTime now);

    // 출발 N분 전 게시글 (드라이버 임박 알림 대상)
    @Query("SELECT p FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' " +
           "AND p.departureTime >= :from AND p.departureTime < :to")
    List<Post> findApproachingOpenPosts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.tags WHERE p.id = :id AND p.deleted = false")
    Optional<Post> findByIdAndDeletedFalseWithTags(@Param("id") Long id);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deleted = false")
    Optional<Post> findByIdAndDeletedFalseWithLock(@Param("id") Long id);

    List<Post> findByMemberIdAndDeletedFalse(Long memberId);

    // 오늘~+48h 출발 게시글만 조회 (캐싱 대상)
    @Query(value = "SELECT p FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' " +
                   "AND p.departureTime >= :from AND p.departureTime < :to ORDER BY p.departureTime ASC",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' " +
                        "AND p.departureTime >= :from AND p.departureTime < :to")
    Page<Post> findUpcomingPaged(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);

    // 날짜/위치(바운딩 박스) 기반 필터링 (선택적 파라미터)
    @Query(value = "SELECT p FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' " +
                   "AND p.departureTime >= CURRENT_TIMESTAMP " +
                   "AND (:fromDate IS NULL OR p.departureTime >= :fromDate) " +
                   "AND (:toDate IS NULL OR p.departureTime < :toDate) " +
                   "AND (:minLat IS NULL OR p.departureLat >= :minLat) " +
                   "AND (:maxLat IS NULL OR p.departureLat <= :maxLat) " +
                   "AND (:minLng IS NULL OR p.departureLng >= :minLng) " +
                   "AND (:maxLng IS NULL OR p.departureLng <= :maxLng) " +
                   "ORDER BY p.departureTime ASC",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deleted = false AND p.status = 'OPEN' " +
                        "AND p.departureTime >= CURRENT_TIMESTAMP " +
                        "AND (:fromDate IS NULL OR p.departureTime >= :fromDate) " +
                        "AND (:toDate IS NULL OR p.departureTime < :toDate) " +
                        "AND (:minLat IS NULL OR p.departureLat >= :minLat) " +
                        "AND (:maxLat IS NULL OR p.departureLat <= :maxLat) " +
                        "AND (:minLng IS NULL OR p.departureLng >= :minLng) " +
                        "AND (:maxLng IS NULL OR p.departureLng <= :maxLng)")
    Page<Post> findFiltered(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            Pageable pageable);
}