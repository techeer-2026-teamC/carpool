package com.techeer.carpool.global.jwt;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final BlacklistRedisRepository blacklistRedisRepository;
    private final JwtClaimsCacheRepository jwtClaimsCacheRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                if (isBlacklisted(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // 캐시 히트: HMAC 검증 스킵
                Long memberId = jwtClaimsCacheRepository.findMemberId(token).orElse(null);
                if (memberId == null) {
                    // 캐시 미스: HMAC 검증 후 캐싱
                    memberId = jwtTokenProvider.getMemberIdFromToken(token);
                    long remaining = jwtTokenProvider.getRemainingSeconds(token);
                    jwtClaimsCacheRepository.save(token, memberId, remaining);
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                request.setAttribute("tokenError", "AUTH_005");
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute("tokenError", "AUTH_004");
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlacklisted(String token) {
        try {
            return blacklistRedisRepository.isBlacklisted(token);
        } catch (Exception e) {
            log.error("Redis 블랙리스트 조회 실패 — 로그아웃된 토큰이 허용될 수 있음: {}", e.getMessage());
            return false;
        }
    }

    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
