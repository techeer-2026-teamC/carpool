package com.techeer.carpool.domain.post;

import tools.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.driver.entity.CarColor;
import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockitoBean RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean RefreshTokenRedisRepository refreshTokenRedisRepository;
    @MockitoBean BlacklistRedisRepository blacklistRedisRepository;
    @MockitoBean RedisNotificationPublisher notificationPublisher;

    private Long ownerId;
    private Long applicantId;
    private Long nonDriverId;
    private Long postId;
    private String ownerToken;
    private String applicantToken;
    private String nonDriverToken;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        postRepository.deleteAll();
        driverRepository.deleteAll();
        memberRepository.deleteAll();

        Member owner = memberRepository.save(Member.builder()
                .email("owner@test.com").password("pw").nickname("작성자").build());
        ownerId = owner.getId();
        ownerToken = "Bearer " + jwtTokenProvider.createAccessToken(ownerId);

        // owner는 driver 등록
        driverRepository.save(Driver.builder()
                .memberId(ownerId)
                .carModel("소나타")
                .carColor(CarColor.WHITE)
                .carNumber("12가3456")
                .build());

        Member applicant = memberRepository.save(Member.builder()
                .email("applicant@test.com").password("pw").nickname("신청자").build());
        applicantId = applicant.getId();
        applicantToken = "Bearer " + jwtTokenProvider.createAccessToken(applicantId);

        Member nonDriver = memberRepository.save(Member.builder()
                .email("nondriver@test.com").password("pw").nickname("비드라이버").build());
        nonDriverId = nonDriver.getId();
        nonDriverToken = "Bearer " + jwtTokenProvider.createAccessToken(nonDriverId);

        Post post = postRepository.save(Post.builder()
                .memberId(ownerId)
                .title("강남 → 판교")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(3)
                .description("테스트")
                .autoAccept(false)
                .build());
        postId = post.getId();
    }

    // ── 게시글 생성 ──────────────────────────────────────────

    @Test
    @DisplayName("게시글 생성 성공 - 드라이버인 경우")
    void createPost_success() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildPostBody(false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("새 카풀"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.currentPassengers").value(0));
    }

    @Test
    @DisplayName("게시글 생성 실패 - 드라이버 미등록")
    void createPost_noDriver() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", nonDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildPostBody(false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_003"));
    }

    @Test
    @DisplayName("게시글 생성 실패 - 미인증")
    void createPost_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 게시글 조회 ──────────────────────────────────────────

    @Test
    @DisplayName("게시글 전체 목록 조회 성공")
    void getAllPosts_success() throws Exception {
        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("강남 → 판교"));
    }

    @Test
    @DisplayName("게시글 목록 조회 성공 - 미인증 허용 (permitAll)")
    void getAllPosts_unauthenticated_allowed() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("게시글 단건 조회 성공")
    void getPost_success() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId))
                .andExpect(jsonPath("$.data.title").value("강남 → 판교"));
    }

    @Test
    @DisplayName("게시글 단건 조회 실패 - 존재하지 않는 ID")
    void getPost_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{id}", 99999L)
                        .header("Authorization", ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_001"));
    }

    @Test
    @DisplayName("게시글 단건 조회 실패 - 삭제된 게시글")
    void getPost_deleted() throws Exception {
        Post post = postRepository.findById(postId).get();
        post.delete();
        postRepository.save(post);

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_001"));
    }

    // ── 게시글 수정 ──────────────────────────────────────────

    @Test
    @DisplayName("게시글 수정 성공")
    void updatePost_success() throws Exception {
        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateBody("수정된 제목"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));
    }

    @Test
    @DisplayName("게시글 수정 실패 - 타인이 수정 시도")
    void updatePost_forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header("Authorization", applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateBody("해킹 제목"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_002"));
    }

    @Test
    @DisplayName("게시글 수정 실패 - 존재하지 않는 게시글")
    void updatePost_notFound() throws Exception {
        mockMvc.perform(patch("/api/v1/posts/{id}", 99999L)
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateBody("제목"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_001"));
    }

    // ── 게시글 삭제 ──────────────────────────────────────────

    @Test
    @DisplayName("게시글 삭제 성공 - 삭제 후 조회 시 404")
    void deletePost_success() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시글이 삭제되었습니다."));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("게시글 삭제 실패 - 타인이 삭제 시도")
    void deletePost_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .header("Authorization", applicantToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_002"));
    }

    @Test
    @DisplayName("게시글 삭제 시 ACCEPTED 신청 REJECTED 처리")
    void deletePost_rejectsAcceptedApplications() throws Exception {
        // 신청자가 신청
        MvcResult applyResult = mockMvc.perform(
                post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicantToken))
                .andExpect(status().isCreated())
                .andReturn();

        Long applicationId = ((Number) JsonPath.read(
                applyResult.getResponse().getContentAsString(), "$.data.id")).longValue();

        // 작성자가 수락
        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk());

        // 게시글 삭제
        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk());

        // 신청 상태 REJECTED 확인
        assertThat(applicationRepository.findByApplicantIdAndStatus(applicantId, ApplicationStatus.ACCEPTED)).isEmpty();
        assertThat(applicationRepository.findByApplicantIdAndStatus(applicantId, ApplicationStatus.REJECTED)).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────

    private Map<String, Object> buildPostBody(boolean autoAccept) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "새 카풀");
        body.put("departureLocation", "홍대입구");
        body.put("departureLat", 37.5572);
        body.put("departureLng", 126.9247);
        body.put("destinationLocation", "여의도");
        body.put("destinationLat", 37.5215);
        body.put("destinationLng", 126.9242);
        body.put("departureTime", LocalDateTime.now().plusDays(2).toString());
        body.put("maxPassengers", 2);
        body.put("description", "직행");
        body.put("autoAccept", autoAccept);
        return body;
    }

    private Map<String, Object> buildUpdateBody(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("departureLocation", "강남역");
        body.put("departureLat", 37.4979);
        body.put("departureLng", 127.0276);
        body.put("destinationLocation", "판교역");
        body.put("destinationLat", 37.3943);
        body.put("destinationLng", 127.1110);
        body.put("departureTime", LocalDateTime.now().plusDays(1).toString());
        body.put("maxPassengers", 3);
        body.put("description", "테스트");
        body.put("autoAccept", false);
        body.put("status", "OPEN");
        return body;
    }
}
