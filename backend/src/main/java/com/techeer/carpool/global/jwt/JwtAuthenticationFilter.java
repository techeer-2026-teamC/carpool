package com.techeer.carpool.global.jwt;

import com.techeer.carpool.domain.auth.repository.BlacklistRedisRepository;
import com.techeer.carpool.domain.member.repository.MemberRepository;
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
    private final MemberRepository members;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                if (blacklistRedisRepository.isBlacklisted(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // A cache entry must never bypass token purpose, signature, or expiry validation.
                jwtTokenProvider.requireAccessToken(token);
                Long memberId = jwtClaimsCacheRepository.findMemberId(token).orElse(null);
                if (memberId == null) {
                    // 캐시 미스: HMAC 검증 후 캐싱
                    memberId = jwtTokenProvider.getMemberIdFromToken(token);
                    long remaining = jwtTokenProvider.getRemainingSeconds(token);
                    jwtClaimsCacheRepository.save(token, memberId, remaining);
                }

                if (!members.existsByIdAndDeletedFalse(memberId)) {
                    SecurityContextHolder.clearContext();
                    request.setAttribute("tokenError", "AUTH_004");
                    filterChain.doFilter(request, response);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (org.springframework.dao.DataAccessException e) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication temporarily unavailable");
                return;
            } catch (ExpiredJwtException e) {
                request.setAttribute("tokenError", "AUTH_005");
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute("tokenError", "AUTH_004");
            }
        }

        filterChain.doFilter(request, response);
    }

    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
