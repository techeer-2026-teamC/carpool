package com.techeer.carpool.domain.application;

import tools.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockitoBean RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean RefreshTokenRedisRepository refreshTokenRedisRepository;
    @MockitoBean BlacklistRedisRepository blacklistRedisRepository;
    @MockitoBean RedisNotificationPublisher notificationPublisher;

    private Long ownerId;
    private Long applicant1Id;
    private Long applicant2Id;
    private Long postId;
    private String ownerToken;
    private String applicant1Token;
    private String applicant2Token;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        postRepository.deleteAll();
        memberRepository.deleteAll();

        Member owner = memberRepository.save(Member.builder()
                .email("owner@test.com").password("pw").nickname("작성자").build());
        ownerId = owner.getId();
        ownerToken = "Bearer " + jwtTokenProvider.createAccessToken(ownerId);

        Member app1 = memberRepository.save(Member.builder()
                .email("app1@test.com").password("pw").nickname("신청자1").build());
        applicant1Id = app1.getId();
        applicant1Token = "Bearer " + jwtTokenProvider.createAccessToken(applicant1Id);

        Member app2 = memberRepository.save(Member.builder()
                .email("app2@test.com").password("pw").nickname("신청자2").build());
        applicant2Id = app2.getId();
        applicant2Token = "Bearer " + jwtTokenProvider.createAccessToken(applicant2Id);

        Post post = postRepository.save(Post.builder()
                .memberId(ownerId)
                .title("카풀 모집")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(3)
                .description("테스트")
                .autoAccept(false)
                .build());
        postId = post.getId();
    }

    // ── 신청 ─────────────────────────────────────────────────

    @Test
    @DisplayName("카풀 신청 성공 - PENDING 상태")
    void apply_success() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicant1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.applicantNickname").value("신청자1"));
    }

    @Test
    @DisplayName("카풀 신청 실패 - 본인 게시글 신청")
    void apply_ownPost() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPLICATION_003"));
    }

    @Test
    @DisplayName("카풀 신청 실패 - 중복 신청")
    void apply_duplicate() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicant1Token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicant1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_002"));
    }

    @Test
    @DisplayName("카풀 신청 실패 - 정원 초과 (수락 후 CLOSED 상태)")
    void apply_postFull() throws Exception {
        Post smallPost = postRepository.save(Post.builder()
                .memberId(ownerId)
                .title("1인 카풀")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(1)
                .autoAccept(false)
                .build());
        Long smallPostId = smallPost.getId();

        MvcResult applyResult = mockMvc.perform(
                post("/api/v1/posts/{postId}/applications", smallPostId)
                        .header("Authorization", applicant1Token))
                .andExpect(status().isCreated()).andReturn();

        Long applicationId = ((Number) JsonPath.read(
                applyResult.getResponse().getContentAsString(), "$.data.id")).longValue();

        // 수락 → currentPassengers=1, CLOSED
        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk());

        // 신청자2 신청 시도 → CLOSED 상태로 차단
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", smallPostId)
                        .header("Authorization", applicant2Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_005"));
    }

    @Test
    @DisplayName("카풀 신청 실패 - 수동 마감된 게시글")
    void apply_manuallyClosed() throws Exception {
        Post closedPost = postRepository.save(Post.builder()
                .memberId(ownerId)
                .title("마감 카풀")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(3)
                .autoAccept(false)
                .build());
        closedPost.close();
        postRepository.save(closedPost);

        mockMvc.perform(post("/api/v1/posts/{postId}/applications", closedPost.getId())
                        .header("Authorization", applicant1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_005"));
    }

    @Test
    @DisplayName("카풀 신청 실패 - 존재하지 않는 게시글")
    void apply_postNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", 99999L)
                        .header("Authorization", applicant1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_001"));
    }

    @Test
    @DisplayName("autoAccept 게시글 신청 - 즉시 ACCEPTED")
    void apply_autoAccept() throws Exception {
        Post autoPost = postRepository.save(Post.builder()
                .memberId(ownerId)
                .title("자동수락 카풀")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(3)
                .autoAccept(true)
                .build());

        mockMvc.perform(post("/api/v1/posts/{postId}/applications", autoPost.getId())
                        .header("Authorization", applicant1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // currentPassengers 증가 확인
        mockMvc.perform(get("/api/v1/posts/{id}", autoPost.getId())
                        .header("Authorization", ownerToken))
                .andExpect(jsonPath("$.data.currentPassengers").value(1));
    }

    // ── 신청 목록 조회 ───────────────────────────────────────

    @Test
    @DisplayName("내 신청 내역 조회 성공")
    void getMyApplications_success() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicant1Token));

        mockMvc.perform(get("/api/v1/applications/me")
                        .header("Authorization", applicant1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].postId").value(postId));
    }

    @Test
    @DisplayName("게시글 신청 목록 조회 성공 - 작성자")
    void getPostApplications_owner() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicant1Token));
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicant2Token));

        mockMvc.perform(get("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("게시글 신청 목록 조회 실패 - 비작성자 접근")
    void getPostApplications_notOwner() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}/applications", postId)
                        .header("Authorization", applicant1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPLICATION_004"));
    }

    // ── 신청 수락/거절 ───────────────────────────────────────

    @Test
    @DisplayName("신청 수락 성공 - ACCEPTED, currentPassengers 증가")
    void accept_success() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken))
                .andExpect(jsonPath("$.data.currentPassengers").value(1));
    }

    @Test
    @DisplayName("신청 거절 성공 - REJECTED")
    void reject_success() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/reject", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("신청 수락 실패 - 이미 처리된 신청")
    void accept_alreadyProcessed() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", ownerToken));

        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_006"));
    }

    @Test
    @DisplayName("신청 수락 실패 - 권한 없음 (비작성자)")
    void accept_forbidden() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", applicant2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPLICATION_004"));
    }

    @Test
    @DisplayName("신청 수락 실패 - 존재하지 않는 신청")
    void accept_notFound() throws Exception {
        mockMvc.perform(patch("/api/v1/applications/{id}/accept", 99999L)
                        .header("Authorization", ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_001"));
    }

    // ── 수락/거절 취소 ───────────────────────────────────────

    @Test
    @DisplayName("수락 취소 성공 - PENDING 복구, currentPassengers 감소")
    void cancelAccept_success() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", ownerToken));

        mockMvc.perform(patch("/api/v1/applications/{id}/cancel-accept", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/posts/{id}", postId)
                        .header("Authorization", ownerToken))
                .andExpect(jsonPath("$.data.currentPassengers").value(0));
    }

    @Test
    @DisplayName("거절 취소 성공 - PENDING 복구")
    void cancelReject_success() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/reject", applicationId)
                        .header("Authorization", ownerToken));

        mockMvc.perform(patch("/api/v1/applications/{id}/cancel-reject", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("수락 취소 실패 - PENDING 상태에서 시도")
    void cancelAccept_notAccepted() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/cancel-accept", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_007"));
    }

    @Test
    @DisplayName("거절 취소 실패 - PENDING 상태에서 시도")
    void cancelReject_notRejected() throws Exception {
        Long applicationId = applyAndGetId(postId, applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/cancel-reject", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_008"));
    }

    @Test
    @DisplayName("수락 취소 성공 - CLOSED 게시글이 OPEN으로 복구")
    void cancelAccept_reopensClosedPost() throws Exception {
        Post smallPost = postRepository.save(Post.builder()
                .memberId(ownerId)
                .title("1인 카풀")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(1)
                .autoAccept(false)
                .build());

        Long applicationId = applyAndGetId(smallPost.getId(), applicant1Token);

        mockMvc.perform(patch("/api/v1/applications/{id}/accept", applicationId)
                        .header("Authorization", ownerToken));

        mockMvc.perform(patch("/api/v1/applications/{id}/cancel-accept", applicationId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/posts/{id}", smallPost.getId())
                        .header("Authorization", ownerToken))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.currentPassengers").value(0));
    }

    // ── helpers ───────────────────────────────────────────────

    private Long applyAndGetId(Long targetPostId, String applicantToken) throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/v1/posts/{postId}/applications", targetPostId)
                        .header("Authorization", applicantToken))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }
}
