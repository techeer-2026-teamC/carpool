package com.techeer.carpool.domain.driver;

import tools.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.auth.repository.RefreshTokenRedisRepository;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriverIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean RedisMessageListenerContainer redisMessageListenerContainer;
    @MockitoBean RefreshTokenRedisRepository refreshTokenRedisRepository;
    @MockitoBean BlacklistRedisRepository blacklistRedisRepository;
    @MockitoBean RedisNotificationPublisher notificationPublisher;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        driverRepository.deleteAll();
        memberRepository.deleteAll();

        memberRepository.save(Member.builder()
                .email("driver@test.com")
                .password(passwordEncoder.encode("password123"))
                .nickname("테스트드라이버")
                .build());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "driver@test.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        accessToken = objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    @AfterEach
    void tearDown() {
        driverRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void registerDriver_success() throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "소나타",
                                "carColor", "WHITE",
                                "carNumber", "12가3456"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.carNumber").value("12가3456"))
                .andExpect(jsonPath("$.data.carModel").value("소나타"))
                .andExpect(jsonPath("$.data.carColor").value("WHITE"))
                .andExpect(jsonPath("$.data.carColorLabel").value("흰색"));
    }

    @Test
    void registerDriver_alreadyRegistered_returns409() throws Exception {
        registerDriver("12가3456");

        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "K5",
                                "carNumber", "99나9999"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_001"));
    }

    @Test
    void registerDriver_carNumberDuplicate_returns409() throws Exception {
        registerDriver("12가3456");

        memberRepository.save(Member.builder()
                .email("driver2@test.com")
                .password(passwordEncoder.encode("password123"))
                .nickname("테스트드라이버2")
                .build());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "driver2@test.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String secondToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "소나타",
                                "carNumber", "12가3456"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_002"));
    }

    @Test
    void registerDriver_missingCarModel_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carNumber", "12가3456"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void registerDriver_missingCarNumber_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "소나타"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void registerDriver_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "소나타",
                                "carNumber", "12가3456"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyDriver_success() throws Exception {
        registerDriver("12가3456");

        mockMvc.perform(get("/api/v1/drivers/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carNumber").value("12가3456"))
                .andExpect(jsonPath("$.data.carModel").value("소나타"));
    }

    @Test
    void getMyDriver_notRegistered_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_003"));
    }

    @Test
    void updateDriver_success() throws Exception {
        registerDriver("12가3456");

        mockMvc.perform(put("/api/v1/drivers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "K5",
                                "carColor", "BLACK",
                                "carNumber", "99나9999"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carNumber").value("99나9999"))
                .andExpect(jsonPath("$.data.carModel").value("K5"))
                .andExpect(jsonPath("$.data.carColor").value("BLACK"));
    }

    @Test
    void updateDriver_notRegistered_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/drivers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "소나타",
                                "carNumber", "12가3456"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_003"));
    }

    private void registerDriver(String carNumber) throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "carModel", "소나타",
                                "carNumber", carNumber
                        ))))
                .andExpect(status().isCreated());
    }
}
