package com.techeer.carpool.domain.apply.service;

import com.techeer.carpool.domain.apply.dto.ApplyResponse;
import com.techeer.carpool.domain.apply.entity.Apply;
import com.techeer.carpool.domain.apply.repository.ApplyRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplyReadService {

    private final ApplyRepository applyRepository;

    public List<ApplyResponse> getMyApplies(Long memberId) {
        return applyRepository.findByMemberIdAndDeletedFalse(memberId)
                .stream()
                .map(ApplyResponse::from)
                .collect(Collectors.toList());
    }

    public ApplyResponse getApplyById(Long applyId) {
        Apply apply = applyRepository.findByIdAndDeletedFalse(applyId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.APPLY_NOT_FOUND));
        return ApplyResponse.from(apply);
    }

    public List<ApplyResponse> getAppliesByPostId(Long postId) {
        return applyRepository.findByPostIdAndDeletedFalse(postId)
                .stream()
                .map(ApplyResponse::from)
                .collect(Collectors.toList());
    }
}