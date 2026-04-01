package com.techeer.carpool.domain.post.service;

import com.techeer.carpool.domain.post.dto.PostCreateRequest;
import com.techeer.carpool.domain.post.dto.PostResponse;
import com.techeer.carpool.domain.post.dto.PostUpdateRequest;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.repository.PostRepository;
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
public class PostService {

    private final PostRepository postRepository;

    @Transactional
    public PostResponse createPost(PostCreateRequest request) {
        Post post = Post.builder()
                .memberId(request.getMemberId())
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

    public List<PostResponse> getAllPosts() {
        return postRepository.findByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(PostResponse::from)
                .collect(Collectors.toList());
    }

    public PostResponse getPostById(Long id) {
        Post post = postRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
        return PostResponse.from(post);
    }

    @Transactional
    public PostResponse updatePost(Long id, PostUpdateRequest request) {
        Post post = postRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
        post.updateFrom(request);
        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(Long id) {
        Post post = postRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
        post.delete();
    }
}
