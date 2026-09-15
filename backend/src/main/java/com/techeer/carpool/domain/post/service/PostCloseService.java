package com.techeer.carpool.domain.post.service;

import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.config.CacheConfig;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostCloseService {
    private final PostRepository postRepository;
    private final CarpoolMetrics carpoolMetrics;

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.UPCOMING_POSTS, allEntries = true)
    public void closePost(Long postId, Long requesterId) {
        Post post = postRepository.findByIdAndDeletedFalseWithLock(postId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
        if (!post.getMemberId().equals(requesterId)) throw new CarpoolException(ErrorCode.POST_FORBIDDEN);
        post.requireBeforeCutoff(LocalDateTime.now());
        if (post.isManuallyClosed()) throw new CarpoolException(ErrorCode.POST_ALREADY_CLOSED);
        post.close();
        carpoolMetrics.incrementPostClosed();
    }
}
