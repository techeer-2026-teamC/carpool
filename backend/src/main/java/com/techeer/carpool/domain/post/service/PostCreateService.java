package com.techeer.carpool.domain.post.service;

import com.techeer.carpool.domain.post.dto.PostCreateRequest;
import com.techeer.carpool.domain.post.dto.PostResponse;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostCreateService {

    private final PostRepository postRepository;

    @Transactional
    public PostResponse createPost(Long memberId, PostCreateRequest request) {
        Post post = Post.builder()
                .memberId(memberId)
                .title(request.getTitle())
                .departureLocation(request.getDepartureLocation())
                .departureLat(request.getDepartureLat())
                .departureLng(request.getDepartureLng())
                .destinationLocation(request.getDestinationLocation())
                .destinationLat(request.getDestinationLat())
                .destinationLng(request.getDestinationLng())
                .departureTime(request.getDepartureTime())
                .maxPassengers(request.getMaxPassengers())
                .description(request.getDescription())
                .autoAccept(request.isAutoAccept())
                .build();

        return PostResponse.from(postRepository.save(post));
    }
}
