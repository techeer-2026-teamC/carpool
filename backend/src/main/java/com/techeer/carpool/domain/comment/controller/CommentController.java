package com.techeer.carpool.domain.comment.controller;

import com.techeer.carpool.domain.comment.dto.CommentRequest;
import com.techeer.carpool.domain.comment.dto.CommentResponse;
import com.techeer.carpool.domain.comment.service.CommentService;
import com.techeer.carpool.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("댓글이 작성되었습니다.", commentService.createComment(postId, request, memberId)));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.of("댓글 목록 조회 성공", commentService.getCommentsByPostId(postId)));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.of("댓글이 수정되었습니다.", commentService.updateComment(commentId, request, memberId)));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        commentService.deleteComment(commentId, memberId);
        return ResponseEntity.ok(ApiResponse.of("댓글이 삭제되었습니다."));
    }
}
