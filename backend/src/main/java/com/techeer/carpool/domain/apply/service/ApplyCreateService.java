package com.techeer.carpool.domain.apply.service;

import com.techeer.carpool.domain.apply.dto.ApplyCreateRequest;
import com.techeer.carpool.domain.apply.dto.ApplyResponse;
import com.techeer.carpool.domain.apply.entity.Apply;
import com.techeer.carpool.domain.apply.repository.ApplyRepository;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplyCreateService {

    private final ApplyRepository applyRepository;
    private final PostRepository postRepository;

    @Transactional
    public ApplyResponse createApply(Long postId, ApplyCreateRequest request) {
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        if (post.getMemberId().equals(request.getMemberId())) {
            throw new CarpoolException(ErrorCode.APPLY_SELF_NOT_ALLOWED);
        }

        if (applyRepository.existsByPostIdAndMemberIdAndDeletedFalse(postId, request.getMemberId())) {
            throw new CarpoolException(ErrorCode.APPLY_ALREADY_EXISTS);
        }

        if (post.getCurrentPassengers() >= post.getMaxPassengers()) {
            throw new CarpoolException(ErrorCode.POST_FULL);
        }

        Apply apply = Apply.builder()
                .postId(postId)
                .memberId(request.getMemberId())
                .build();

        Apply saved = applyRepository.save(apply);

        if (post.isAutoAccept()) {
            saved.accept();
            post.increasePassengers();
        }

        return ApplyResponse.from(saved);
    }
}