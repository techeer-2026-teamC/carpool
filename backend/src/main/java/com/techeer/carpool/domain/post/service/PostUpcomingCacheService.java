package com.techeer.carpool.domain.post.service;

import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.dto.PostPageCache;
import com.techeer.carpool.domain.post.dto.PostSummaryResponse;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게시글 캐싱 전담 서비스.
 * PostService 내부 호출 시 Spring AOP 프록시를 우회하는 문제를 방지하기 위해 분리.
 * Page<T> 는 Redis 역직렬화가 불가능하므로, PostPageCache(List + totalElements)를 캐싱하고
 * PostService에서 PageImpl로 재조립.
 */
@Service
@RequiredArgsConstructor
public class PostUpcomingCacheService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final DriverRepository driverRepository;

    @Cacheable(cacheNames = CacheConfig.UPCOMING_POSTS, key = "#page + ':' + #size")
    @Transactional(readOnly = true)
    public PostPageCache getUpcoming(int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime boundary = now.plusHours(48);
        Page<Post> postPage = postRepository.findUpcomingPaged(now, boundary, PageRequest.of(page, size));

        List<Long> ids = postPage.stream().map(Post::getId).collect(Collectors.toList());
        Map<Long, Post> postsWithTags = postRepository.findByIdsWithTags(ids).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));

        Set<Long> memberIds = postPage.stream().map(Post::getMemberId).collect(Collectors.toSet());
        Map<Long, String> nicknameMap = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
        Map<Long, Double> ratingMap = driverRepository.findByMemberIdInAndDeletedFalse(memberIds).stream()
                .collect(Collectors.toMap(Driver::getMemberId, Driver::getAverageRating));

        List<PostSummaryResponse> content = postPage.stream()
                .map(p -> PostSummaryResponse.from(
                        postsWithTags.getOrDefault(p.getId(), p),
                        nicknameMap.getOrDefault(p.getMemberId(), "알 수 없음"),
                        ratingMap.getOrDefault(p.getMemberId(), 0.0)))
                .collect(Collectors.toList());

        return new PostPageCache(content, postPage.getTotalElements());
    }
}
