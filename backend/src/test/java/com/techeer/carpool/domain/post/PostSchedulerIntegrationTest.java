package com.techeer.carpool.domain.post;

import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostStatus;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.post.scheduler.PostScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringJUnitConfig(PostSchedulerIntegrationTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create", "spring.flyway.enabled=false"})
class PostSchedulerIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @TestConfiguration @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Post.class, Member.class})
    @EnableJpaRepositories(basePackageClasses = {PostRepository.class, MemberRepository.class})
    static class Config { }
    @Autowired PostRepository posts;
    @Autowired MemberRepository members;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean NotificationService notifications;
    @org.junit.jupiter.api.BeforeEach void clearPosts() { posts.deleteAll(); members.deleteAll(); }
    PostScheduler scheduler() { return new PostScheduler(posts, notifications, transactions); }
    Post post(LocalDateTime departure) {
        long owner = members.save(Member.builder().email(UUID.randomUUID()+"@test.com").password("pw").nickname("모집자").build()).getId();
        return posts.save(Post.builder().memberId(owner).title("출발 알림").departureLocation("출발").destinationLocation("도착")
                .departureTime(departure).maxPassengers(2).build());
    }
    @Test void competingReminderTransactionsSaveOnlyOnce() throws Exception {
        Post post = post(LocalDateTime.now().plusMinutes(30));
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); scheduler().notifyApproachingDeparture(); return null; });
            var second = pool.submit(() -> { start.await(); scheduler().notifyApproachingDeparture(); return null; });
            start.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
            verify(notifications, times(1)).save(any());
            assertThat(posts.findById(post.getId()).orElseThrow().getDepartureNotifiedAt()).isNotNull();
        } finally { pool.shutdownNow(); }
    }
    @Test void failedNotificationDoesNotConsumeReminderAndExpiredPostCloses() {
        Post upcoming = post(LocalDateTime.now().plusMinutes(30));
        doThrow(new IllegalStateException("failed save")).when(notifications).save(any());
        assertThatThrownBy(() -> scheduler().notifyApproachingDeparture()).isInstanceOf(IllegalStateException.class);
        assertThat(posts.findById(upcoming.getId()).orElseThrow().getDepartureNotifiedAt()).isNull();
        Post expired = post(LocalDateTime.now().minusMinutes(1));
        scheduler().autoCloseExpiredPosts();
        assertThat(posts.findById(expired.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.CLOSED);
    }
}
