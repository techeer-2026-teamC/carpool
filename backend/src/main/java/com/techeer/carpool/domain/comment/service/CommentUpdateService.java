package com.techeer.carpool.domain.comment.service;

import com.techeer.carpool.domain.comment.dto.CommentResponse;
import com.techeer.carpool.domain.comment.dto.CommentUpdateRequest;
import com.techeer.carpool.domain.comment.entity.Comment;
import com.techeer.carpool.domain.comment.repository.CommentRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentUpdateService {

    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public CommentResponse updateComment(Long commentId, CommentUpdateRequest request, Long memberId) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getMemberId().equals(memberId)) {
            throw new CarpoolException(ErrorCode.COMMENT_FORBIDDEN);
        }

        comment.update(request.getContent());

        String nickname = memberRepository.findById(memberId)
                .map(Member::getNickname)
                .orElse("알 수 없음");

        return CommentResponse.from(comment, nickname);
    }
}
