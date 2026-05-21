package com.techeer.carpool.domain.comment.service;

import com.techeer.carpool.domain.comment.dto.CommentRequest;
import com.techeer.carpool.domain.comment.dto.CommentResponse;
import com.techeer.carpool.domain.comment.entity.Comment;
import com.techeer.carpool.domain.comment.repository.CommentRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public CommentResponse createComment(Long postId, CommentRequest request, Long memberId) {
        postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        Comment comment = Comment.builder()
                .postId(postId)
                .memberId(memberId)
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(comment);
        return CommentResponse.from(saved, resolveNickname(memberId));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByPostId(Long postId) {
        postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        List<Comment> comments = commentRepository.findByPostIdAndDeletedFalseOrderByCreatedAtAsc(postId);

        Set<Long> memberIds = comments.stream()
                .map(Comment::getMemberId)
                .collect(Collectors.toSet());

        Map<Long, String> nicknameMap = resolveNicknameMap(memberIds);

        return comments.stream()
                .map(c -> CommentResponse.from(c, nicknameMap.getOrDefault(c.getMemberId(), "알 수 없음")))
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, CommentRequest request, Long memberId) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getMemberId().equals(memberId)) {
            throw new CarpoolException(ErrorCode.COMMENT_FORBIDDEN);
        }

        comment.update(request.getContent());
        return CommentResponse.from(comment, resolveNickname(memberId));
    }

    @Transactional
    public void deleteComment(Long commentId, Long memberId) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getMemberId().equals(memberId)) {
            throw new CarpoolException(ErrorCode.COMMENT_FORBIDDEN);
        }

        comment.delete();
    }

    private String resolveNickname(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.MEMBER_NOT_FOUND))
                .getNickname();
    }

    private Map<Long, String> resolveNicknameMap(Set<Long> memberIds) {
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
    }
}
