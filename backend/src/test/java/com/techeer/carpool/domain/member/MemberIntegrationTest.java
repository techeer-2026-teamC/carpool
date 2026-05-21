package com.techeer.carpool.domain.member;

import tools.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired PostRepository postRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean RefreshTokenRedisRepository refreshTokenRedisRepository;
    @MockitoBean BlacklistRedisRepository blacklistRedisRepository;
    @MockitoBean RedisNotificationPublisher notificationPublisher;

    private Long memberId;
    private Long otherMemberId;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        postRepository.deleteAll();
        driverRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = memberRepository.save(Member.builder()
                .email("me@test.com")
                .password(passwordEncoder.encode("password123"))
                .nickname("나").build());
        memberId = member.getId();
        token = "Bearer " + jwtTokenProvider.createAccessToken(memberId);

        Member other = memberRepository.save(Member.builder()
                .email("other@test.com")
                .password(passwordEncoder.encode("password123"))
                .nickname("타인").build());
        otherMemberId = other.getId();
        otherToken = "Bearer " + jwtTokenProvider.createAccessToken(otherMemberId);
    }

    // ── 프로필 조회 ──────────────────────────────────────────

    @Test
    @DisplayName("내 프로필 조회 성공")
    void getMyProfile_success() throws Exception {
        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("나"));
    }

    @Test
    @DisplayName("프로필 조회 실패 - 미인증")
    void getMyProfile_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ID로 프로필 조회 성공")
    void getProfileById_success() throws Exception {
        mockMvc.perform(get("/api/v1/members/{id}", memberId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(memberId));
    }

    // ── 프로필 수정 ──────────────────────────────────────────

    @Test
    @DisplayName("닉네임 수정 성공")
    void updateProfile_nickname() throws Exception {
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "새닉네임"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void updateProfile_password() throws Exception {
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "password123",
                                "newPassword", "newPassword456"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
    void updateProfile_wrongCurrentPassword() throws Exception {
        mockMvc.perform(put("/api/v1/members/me")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrongPassword",
                                "newPassword", "newPassword456"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    // ── 회원 탈퇴 ────────────────────────────────────────────

    @Test
    @DisplayName("회원 탈퇴 성공 - 탈퇴 후 로그인 불가")
    void withdraw_success() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "me@test.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_002"));
    }

    @Test
    @DisplayName("회원 탈퇴 시 Driver 소프트 삭제")
    void withdraw_deletesDriver() throws Exception {
        driverRepository.save(Driver.builder()
                .memberId(memberId)
                .carModel("소나타")
                .carColor(CarColor.WHITE)
                .carNumber("12가3456")
                .build());

        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(driverRepository.findByMemberIdAndDeletedFalse(memberId)).isEmpty();
    }

    @Test
    @DisplayName("회원 탈퇴 시 게시글 소프트 삭제")
    void withdraw_deletesPost() throws Exception {
        Post post = postRepository.save(Post.builder()
                .memberId(memberId)
                .title("카풀")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(3)
                .autoAccept(false)
                .build());

        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(postRepository.findByIdAndDeletedFalse(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("회원 탈퇴 시 ACCEPTED 신청 REJECTED 처리")
    void withdraw_rejectsAcceptedApplications() throws Exception {
        Post post = postRepository.save(Post.builder()
                .memberId(otherMemberId)
                .title("카풀")
                .departureLocation("강남역").departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역").destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1))
                .maxPassengers(3)
                .autoAccept(true)
                .build());

        // autoAccept=true이므로 신청 즉시 ACCEPTED
        mockMvc.perform(post("/api/v1/posts/{postId}/applications", post.getId())
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // 탈퇴
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        // ACCEPTED 신청이 REJECTED 처리됐는지 확인
        assertThat(applicationRepository.findByApplicantIdAndStatus(memberId, ApplicationStatus.ACCEPTED)).isEmpty();
        assertThat(applicationRepository.findByApplicantIdAndStatus(memberId, ApplicationStatus.REJECTED)).hasSize(1);
    }
}
