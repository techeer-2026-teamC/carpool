package com.techeer.carpool.domain.member.controller;

import com.techeer.carpool.domain.member.dto.AuthTokens;
import com.techeer.carpool.domain.member.dto.LoginRequest;
import com.techeer.carpool.domain.member.dto.SignupRequest;
import com.techeer.carpool.domain.member.dto.TokenResponse;
import com.techeer.carpool.domain.member.service.MemberLoginService;
import com.techeer.carpool.domain.member.service.MemberSignupService;
import com.techeer.carpool.domain.member.service.MemberWithdrawService;
import com.techeer.carpool.domain.member.service.TokenReissueService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberSignupService memberSignupService;
    private final MemberLoginService memberLoginService;
    private final TokenReissueService tokenReissueService;
    private final MemberWithdrawService memberWithdrawService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.cookie-secure}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        memberSignupService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        AuthTokens tokens = memberLoginService.login(request);
        setRefreshTokenCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(tokens.accessToken())
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> reissue(HttpServletRequest request,
                                                 HttpServletResponse response) {
        String refreshToken = extractRefreshTokenCookie(request);
        AuthTokens tokens = tokenReissueService.reissue(refreshToken);
        setRefreshTokenCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(tokens.accessToken())
                .build());
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(Authentication authentication,
                                         HttpServletResponse response) {
        Long memberId = (Long) authentication.getPrincipal();
        memberWithdrawService.withdraw(memberId);
        clearRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
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

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .sameSite("Strict")
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(0)
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
