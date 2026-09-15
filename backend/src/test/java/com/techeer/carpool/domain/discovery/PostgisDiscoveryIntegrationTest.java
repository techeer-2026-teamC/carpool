package com.techeer.carpool.domain.discovery;

import com.techeer.carpool.domain.application.dto.ApplicationResponse;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.application.service.ApplicationCreateService;
import com.techeer.carpool.domain.application.service.ApplicationStatusService;
import com.techeer.carpool.domain.discovery.dto.*;
import com.techeer.carpool.domain.discovery.service.DiscoveryService;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.entity.*;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.post.service.PostCloseService;
import com.techeer.carpool.domain.notification.repository.NotificationRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "app.legacy-ride.enabled=false"})
@ActiveProfiles("test")
class PostgisDiscoveryIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getenv().getOrDefault("MOA_POSTGIS_IMAGE", "postgis/postgis:15-3.5")).asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired ApplicationCreateService createService;
    @Autowired ApplicationStatusService statusService;
    @Autowired ApplicationRepository applications;
    @Autowired PostRepository posts;
    @Autowired MemberRepository members;
    @Autowired NotificationRepository notifications;
    @Autowired PostCloseService closeService;
    @Autowired DiscoveryService discovery;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean(name = "listenerContainer") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean RedisCacheManager cacheManager;
    long owner;
    long passenger1;
    long passenger2;

    @BeforeEach void createMembers() {
        org.mockito.Mockito.when(cacheManager.getCache(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new org.springframework.cache.concurrent.ConcurrentMapCache("upcoming-posts"));
        owner = member(); passenger1 = member(); passenger2 = member();
    }
    private long member() {
        return members.save(Member.builder().email(UUID.randomUUID() + "@test.com").password("pw").nickname("참여자").build()).getId();
    }
    private Post post(int capacity, PostType type) {
        return posts.save(Post.builder().memberId(owner).title("모아 모집").type(type)
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusHours(2)).maxPassengers(capacity).price(3000).build());
    }

    @Test void postgisFiltersBothCirclesBeforeStablePaginationAndKeepsGeneratedPointsInSync() {
        LocalDateTime departure = LocalDateTime.now().plusYears(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        Post excluded = searchPost(departure, 37.1, PostType.TAXI);
        Post first = searchPost(departure, 37.3943, PostType.TAXI);
        Post second = searchPost(departure, 37.3944, PostType.TAXI);
        searchPost(departure, 37.3943, PostType.CARPOOL);
        DiscoveryRequest query = query(departure, null);
        DiscoveryPage page1 = discovery.search(query);
        assertThat(page1.items()).extracting(p -> p.getId()).containsExactly(first.getId());
        assertThat(page1.hasNext()).isTrue();
        DiscoveryPage page2 = discovery.search(query(departure, page1.nextCursor()));
        assertThat(page2.items()).extracting(p -> p.getId()).containsExactly(second.getId());
        assertThat(page2.hasNext()).isFalse();
        Double lng = jdbc.queryForObject("select ST_X(origin_geography::geometry) from posts where id = ?", Double.class, first.getId());
        assertThat(lng).isEqualTo(127.0276);
        jdbc.update("update posts set destination_lat = 37.3943 where id = ?", excluded.getId());
        assertThat(discovery.search(query).items().get(0).getId()).isEqualTo(excluded.getId());
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where tablename = 'posts' and indexdef like '%USING gist%'", Integer.class)).isEqualTo(2);
    }

    private Post searchPost(LocalDateTime time, double destinationLat, PostType type) {
        return posts.save(Post.builder().memberId(owner).title("공간 검색").type(type)
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(destinationLat).destinationLng(127.1110)
                .departureTime(time).maxPassengers(3).build());
    }
    private DiscoveryRequest query(LocalDateTime time, String cursor) {
        return new DiscoveryRequest(PostType.TAXI, time.minusMinutes(1), time.plusMinutes(1),
                37.4979, 127.0276, 1000.0, 37.3943, 127.1110, 1000.0, 1, cursor);
    }
}
