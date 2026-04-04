package com.techeer.carpool.domain.apply.service;

import com.techeer.carpool.domain.apply.dto.ApplyResponse;
import com.techeer.carpool.domain.apply.dto.ApplyUpdateRequest;
import com.techeer.carpool.domain.apply.entity.Apply;
import com.techeer.carpool.domain.apply.entity.ApplyStatus;
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
public class ApplyUpdateService {

    private final ApplyRepository applyRepository;
    private final PostRepository postRepository;

    @Transactional
    public ApplyResponse updateApply(Long applyId, ApplyUpdateRequest request) {
        Apply apply = applyRepository.findByIdAndDeletedFalse(applyId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.APPLY_NOT_FOUND));

        if (apply.getStatus() != ApplyStatus.PENDING) {
            throw new CarpoolException(ErrorCode.APPLY_ALREADY_PROCESSED);
        }

        if (request.isAccepted()) {
            Post post = postRepository.findByIdAndDeletedFalse(apply.getPostId())
                    .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

            if (post.getCurrentPassengers() >= post.getMaxPassengers()) {
                throw new CarpoolException(ErrorCode.POST_FULL);
            }

            apply.accept();
            post.increasePassengers();
        } else {
            apply.reject(request.getRejectReason());
        }

        return ApplyResponse.from(apply);
    }
}