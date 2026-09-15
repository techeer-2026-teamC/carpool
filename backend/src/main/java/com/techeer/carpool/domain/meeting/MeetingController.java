package com.techeer.carpool.domain.meeting;

import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts/{postId}/meeting")
@RequiredArgsConstructor
public class MeetingController {
    private final MeetingService meetings;
    private final MeetingLocations locations;
    public record Attendance(@NotBlank @Pattern(regexp="MET|NO_SHOW") String status) {}

    @GetMapping
    public ApiResponse<MeetingView> get(@PathVariable Long postId, Authentication auth) {
        return ApiResponse.of("만남 조회", meetings.get(postId, (Long) auth.getPrincipal()));
    }
    @PatchMapping("/participants/{memberId}")
    public ApiResponse<MeetingView> mark(@PathVariable Long postId, @PathVariable Long memberId,
            @Valid @RequestBody Attendance request, Authentication auth) {
        return ApiResponse.of("만남 상태 변경", meetings.mark(postId, memberId, request.status(), (Long) auth.getPrincipal()));
    }
    @PostMapping("/complete")
    public ApiResponse<MeetingView> complete(@PathVariable Long postId, Authentication auth) {
        MeetingView result = meetings.complete(postId, (Long) auth.getPrincipal());
        locations.clear(result);
        return ApiResponse.of("만남 완료", result);
    }
    @GetMapping("/locations")
    public ApiResponse<java.util.List<MeetingLocations.Position>> positions(@PathVariable Long postId, Authentication auth) {
        return ApiResponse.of("최근 위치", locations.get(postId, (Long) auth.getPrincipal()));
    }
    @DeleteMapping("/locations/me")
    public ApiResponse<Void> stop(@PathVariable Long postId, Authentication auth) {
        locations.stop(postId, (Long) auth.getPrincipal());
        return ApiResponse.of("위치 공유 중지");
    }
}
