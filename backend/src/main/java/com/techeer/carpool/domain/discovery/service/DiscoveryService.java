package com.techeer.carpool.domain.discovery.service;

import com.techeer.carpool.domain.discovery.dto.*;
import com.techeer.carpool.domain.discovery.repository.DiscoveryRepository;
import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.dto.PostSummaryResponse;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscoveryService {
    private final DiscoveryRepository discoveryRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final DriverRepository driverRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DiscoveryPage search(DiscoveryRequest request) {
        request.validate();
        List<Long> ids = discoveryRepository.searchIds(request, DiscoveryCursor.decode(request.cursor()));
        boolean hasNext = ids.size() > request.limit();
        List<Long> pageIds = ids.subList(0, Math.min(ids.size(), request.limit()));
        if (pageIds.isEmpty()) return new DiscoveryPage(List.of(), null, false);
        Map<Long, Post> posts = postRepository.findByIdsWithTags(pageIds).stream().collect(Collectors.toMap(Post::getId, p -> p));
        Set<Long> owners = posts.values().stream().map(Post::getMemberId).collect(Collectors.toSet());
        Map<Long, String> names = memberRepository.findAllById(owners).stream().collect(Collectors.toMap(Member::getId, Member::getNickname));
        Map<Long, Double> ratings = driverRepository.findByMemberIdInAndDeletedFalse(owners).stream().collect(Collectors.toMap(Driver::getMemberId, Driver::getAverageRating));
        List<PostSummaryResponse> items = pageIds.stream().map(posts::get)
                .map(p -> PostSummaryResponse.from(p, names.getOrDefault(p.getMemberId(), "알 수 없음"), ratings.getOrDefault(p.getMemberId(), 0.0))).toList();
        Post last = posts.get(pageIds.get(pageIds.size() - 1));
        return new DiscoveryPage(items, hasNext ? new DiscoveryCursor(last.getDepartureTime(), last.getId()).encode() : null, hasNext);
    }
}
