package com.techeer.carpool.domain.expense;

import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts/{postId}/expense")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenses;
    public record Total(@NotNull @Min(0) @Max(10000000) Integer total) {}
    public record Collection(@NotNull Boolean collected) {}
    @GetMapping
    public ApiResponse<ExpenseService.View> get(@PathVariable Long postId,Authentication auth) {
        return ApiResponse.of("외부 비용 분담 기록",expenses.get(postId,(Long)auth.getPrincipal()));
    }
    @PutMapping
    public ApiResponse<ExpenseService.View> total(@PathVariable Long postId,@Valid @RequestBody Total body,Authentication auth) {
        return ApiResponse.of("비용 분담 저장",expenses.setTotal(postId,body.total(),(Long)auth.getPrincipal()));
    }
    @PatchMapping("/members/{memberId}/collection")
    public ApiResponse<ExpenseService.View> collect(@PathVariable Long postId,@PathVariable Long memberId,
            @Valid @RequestBody Collection body,Authentication auth) {
        return ApiResponse.of("외부 수금 기록 변경",expenses.collect(postId,memberId,body.collected(),(Long)auth.getPrincipal()));
    }
    @GetMapping("/history")
    public ApiResponse<List<ExpenseService.Audit>> history(@PathVariable Long postId,Authentication auth) {
        return ApiResponse.of("비용 변경 이력",expenses.history(postId,(Long)auth.getPrincipal()));
    }
}
