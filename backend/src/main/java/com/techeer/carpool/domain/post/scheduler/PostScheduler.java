package com.techeer.carpool.domain.post.scheduler;

import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.notification.type.NotificationType;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostScheduler {

    private static final String DEPARTURE_NOTIF_KEY = "departure_notif:";
    private static final int APPROACHING_MINUTES = 60;

    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final RedisNotificationPublisher notificationPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheManager cacheManager;

    /**
     * 출발 시간이 지난 OPEN 게시글을 1분마다 자동 마감.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void autoCloseExpiredPosts() {
        List<Post> expired = postRepository.findExpiredOpenPosts(LocalDateTime.now());
        if (expired.isEmpty()) return;

        expired.forEach(Post::close);
        evictUpcomingCache();
        log.info("자동 마감 완료: {}건", expired.size());
    }

    /**
     * 출발 1시간 전 드라이버에게 알림을 5분마다 체크.
     * Redis key로 중복 발송 방지.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void notifyApproachingDeparture() {
        LocalDateTime from = LocalDateTime.now().plusMinutes(APPROACHING_MINUTES - 5);
        LocalDateTime to   = LocalDateTime.now().plusMinutes(APPROACHING_MINUTES + 5);

        List<Post> approaching = postRepository.findApproachingOpenPosts(from, to);
        List<Post> unnotified = approaching.stream()
                .filter(p -> Boolean.FALSE.equals(
                        stringRedisTemplate.hasKey(DEPARTURE_NOTIF_KEY + p.getId())))
                .collect(Collectors.toList());

        if (unnotified.isEmpty()) return;

        notificationService.saveAll(unnotified.stream()
                .map(p -> Notification.ofDepartureApproaching(p.getMemberId(), p.getId()))
                .collect(Collectors.toList()));

        unnotified.forEach(p -> {
            notificationPublisher.publish(p.getMemberId(), NotificationPayload.builder()
                    .type(NotificationType.DEPARTURE_APPROACHING)
                    .message("1시간 후 출발 예정입니다. 카풀을 마감해 주세요.")
                    .data(Map.of("postId", p.getId()))
                    .build());
            stringRedisTemplate.opsForValue()
                    .set(DEPARTURE_NOTIF_KEY + p.getId(), "1", Duration.ofHours(3));
        });

        log.info("출발 임박 알림 발송: {}건", unnotified.size());
    }

    private void evictUpcomingCache() {
        var cache = cacheManager.getCache(CacheConfig.UPCOMING_POSTS);
        if (cache != null) cache.clear();
    }
}
