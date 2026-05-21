package com.techeer.carpool.domain.comment.dto;

import com.techeer.carpool.domain.comment.entity.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommentResponse {

    private Long id;
    private Long postId;
    private Long memberId;
    private String nickname;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommentResponse from(Comment comment, String nickname) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .memberId(comment.getMemberId())
                .nickname(nickname)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
