package com.techeer.carpool.domain.auth.controller;

import com.techeer.carpool.domain.auth.dto.AuthTokens;
import com.techeer.carpool.domain.auth.dto.LoginRequest;
import com.techeer.carpool.domain.auth.dto.SignupRequest;
import com.techeer.carpool.domain.auth.dto.TokenResponse;
import com.techeer.carpool.domain.auth.service.MemberLoginService;
import com.techeer.carpool.domain.auth.service.MemberSignupService;
import com.techeer.carpool.domain.auth.service.TokenReissueService;
import com.techeer.carpool.global.common.ApiResponse;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberSignupService memberSignupService;
    private final MemberLoginService memberLoginService;
    private final TokenReissueService tokenReissueService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.cookie-secure}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
        memberSignupService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("회원가입이 완료되었습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletResponse response) {
        AuthTokens tokens = memberLoginService.login(request);
        setRefreshTokenCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(ApiResponse.of("로그인 성공",
                TokenResponse.builder().accessToken(tokens.accessToken()).build()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request,
                                                              HttpServletResponse response) {
        String refreshToken = extractRefreshTokenCookie(request);
        AuthTokens tokens = tokenReissueService.reissue(refreshToken);
        setRefreshTokenCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(ApiResponse.of("토큰 재발급 성공",
                TokenResponse.builder().accessToken(tokens.accessToken()).build()));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .sameSite("Strict")
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(jwtTokenProvider.getRefreshTokenExpirationSeconds())
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new CarpoolException(ErrorCode.INVALID_TOKEN);
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new CarpoolException(ErrorCode.INVALID_TOKEN));
    }
}
