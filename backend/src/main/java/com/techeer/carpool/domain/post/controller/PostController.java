package com.techeer.carpool.domain.post.controller;

import com.techeer.carpool.domain.post.dto.PostCreateRequest;
import com.techeer.carpool.domain.post.dto.PostDetailResponse;
import com.techeer.carpool.domain.post.dto.PostSummaryResponse;
import com.techeer.carpool.domain.post.dto.PostUpdateRequest;
import com.techeer.carpool.domain.post.service.PostCloseService;
import com.techeer.carpool.domain.post.service.PostService;
import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostCloseService postCloseService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostDetailResponse>> createPost(
            @Valid @RequestBody PostCreateRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("게시글이 생성되었습니다.", postService.createPost(request, memberId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostSummaryResponse>>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.of("게시글 목록 조회 성공", postService.getUpcomingPagedPosts(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPostById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of("게시글 조회 성공", postService.getPostById(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("게시글이 수정되었습니다.", postService.updatePost(id, request, memberId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long id,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        postService.deletePost(id, memberId);
        return ResponseEntity.ok(ApiResponse.of("게시글이 삭제되었습니다."));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<Void>> closePost(
            @PathVariable Long id,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        postCloseService.closePost(id, memberId);
        return ResponseEntity.ok(ApiResponse.of("카풀이 마감되었습니다."));
    }
}
