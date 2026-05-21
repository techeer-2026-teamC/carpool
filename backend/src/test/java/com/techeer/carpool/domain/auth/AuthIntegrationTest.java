package com.techeer.carpool.domain.auth;

import tools.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
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

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean RefreshTokenRedisRepository refreshTokenRedisRepository;
    @MockitoBean BlacklistRedisRepository blacklistRedisRepository;
    @MockitoBean RedisNotificationPublisher notificationPublisher;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    // ── 회원가입 ─────────────────────────────────────────────

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "new@test.com",
                                "password", "password123",
                                "nickname", "신규유저"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_emailDuplicate() throws Exception {
        memberRepository.save(Member.builder()
                .email("dup@test.com").password("pw").nickname("기존유저").build());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "dup@test.com",
                                "password", "password123",
                                "nickname", "중복유저"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 형식 오류")
    void signup_invalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "password", "password123",
                                "nickname", "유저"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호 8자 미만")
    void signup_shortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "valid@test.com",
                                "password", "short",
                                "nickname", "유저"
                        ))))
                .andExpect(status().isBadRequest());
    }

    // ── 로그인 ───────────────────────────────────────────────

    @Test
    @DisplayName("로그인 성공 - accessToken 반환, refreshToken 쿠키 설정")
    void login_success() throws Exception {
        memberRepository.save(Member.builder()
                .email("user@test.com")
                .password(passwordEncoder.encode("password123"))
                .nickname("유저").build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일")
    void login_memberNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ghost@test.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_002"));
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_wrongPassword() throws Exception {
        memberRepository.save(Member.builder()
                .email("user@test.com")
                .password(passwordEncoder.encode("correctPassword"))
                .nickname("유저").build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "wrongPassword"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("로그인 실패 - 탈퇴한 회원")
    void login_withdrawnMember() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .email("withdrawn@test.com")
                .password(passwordEncoder.encode("password123"))
                .nickname("탈퇴유저").build());
        member.withdraw();
        memberRepository.save(member);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "withdrawn@test.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_002"));
    }

    // ── 토큰 재발급 ──────────────────────────────────────────

    @Test
    @DisplayName("토큰 재발급 성공")
    void refresh_success() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .email("user@test.com")
                .password(passwordEncoder.encode("password123"))
                .nickname("유저").build());

        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());
        when(refreshTokenRedisRepository.findByMemberId(member.getId()))
                .thenReturn(Optional.of(refreshToken));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 쿠키 없음")
    void refresh_noCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 유효하지 않은 토큰")
    void refresh_invalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "invalid.token.value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - Redis에 없는 토큰 (탈취 감지)")
    void refresh_tokenNotInRedis() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .email("user@test.com")
                .password(passwordEncoder.encode("pw"))
                .nickname("유저").build());

        String orphanToken = jwtTokenProvider.createRefreshToken(member.getId());
        when(refreshTokenRedisRepository.findByMemberId(any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", orphanToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }
}
