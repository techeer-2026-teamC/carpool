package com.techeer.carpool.domain.post.service;

import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.entity.ApplicationStatus;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.comment.dto.CommentResponse;
import com.techeer.carpool.domain.comment.service.CommentService;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.notification.dto.NotificationPayload;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.publisher.RedisNotificationPublisher;
import com.techeer.carpool.domain.notification.service.NotificationService;
import com.techeer.carpool.domain.notification.type.NotificationType;
import com.techeer.carpool.domain.post.dto.PostCreateRequest;
import com.techeer.carpool.domain.post.dto.PostDetailResponse;
import com.techeer.carpool.domain.post.dto.PostSummaryResponse;
import com.techeer.carpool.domain.post.dto.PostUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostUpdateCommand;
import com.techeer.carpool.domain.post.entity.Tag;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.post.repository.TagRepository;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import com.techeer.carpool.global.metrics.CarpoolMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final TagRepository tagRepository;
    private final ApplicationRepository applicationRepository;
    private final CommentService commentService;
    private final DriverRepository driverRepository;
    private final RedisNotificationPublisher notificationPublisher;
    private final NotificationService notificationService;
    private final CarpoolMetrics carpoolMetrics;

    @Transactional
    public PostDetailResponse createPost(PostCreateRequest request, Long memberId) {
        driverRepository.findByMemberIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new CarpoolException(ErrorCode.DRIVER_NOT_FOUND));

        List<Tag> tags = resolveTags(request.getTagIds());
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
                .price(request.getPrice())
                .tags(tags)
                .build();
        Post saved = postRepository.save(post);
        carpoolMetrics.incrementPostCreated();
        return PostDetailResponse.from(saved, fetchNickname(saved.getMemberId()), List.of());
    }

    @Transactional(readOnly = true)
    public List<PostSummaryResponse> getAllPosts() {
        List<Post> posts = postRepository.findByDeletedFalseWithTagsOrderByCreatedAtDesc();
        Set<Long> memberIds = posts.stream().map(Post::getMemberId).collect(Collectors.toSet());
        Map<Long, String> nicknameMap = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
        return posts.stream()
                .map(p -> PostSummaryResponse.from(p, nicknameMap.getOrDefault(p.getMemberId(), "알 수 없음")))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryResponse> getPagedPosts(Pageable pageable) {
        // 1단계: SQL LIMIT/OFFSET으로 페이지 ID 목록 조회
        Page<Post> postPage = postRepository.findPageByDeletedFalse(pageable);
        List<Long> ids = postPage.stream().map(Post::getId).collect(Collectors.toList());

        // 2단계: 해당 ID들만 tags 배치 로드 (N+1 방지)
        Map<Long, Post> postsWithTags = postRepository.findByIdsWithTags(ids).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));

        Set<Long> memberIds = postPage.stream().map(Post::getMemberId).collect(Collectors.toSet());
        Map<Long, String> nicknameMap = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));

        return postPage.map(p -> PostSummaryResponse.from(
                postsWithTags.getOrDefault(p.getId(), p),
                nicknameMap.getOrDefault(p.getMemberId(), "알 수 없음")));
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPostById(Long id) {
        Post post = postRepository.findByIdAndDeletedFalseWithTags(id)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));

        List<CommentResponse> commentResponses = commentService.getCommentsByPostId(id);

        return PostDetailResponse.from(post, fetchNickname(post.getMemberId()), commentResponses);
    }

    @Transactional
    public PostDetailResponse updatePost(Long id, PostUpdateRequest request, Long requesterId) {
        Post post = postRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
        validateOwner(post, requesterId);
        List<Tag> tags = resolveTags(request.getTagIds());
        post.updateFrom(new PostUpdateCommand(
                request.getTitle(),
                request.getDepartureLocation(),
                request.getDepartureLat(),
                request.getDepartureLng(),
                request.getDestinationLocation(),
                request.getDestinationLat(),
                request.getDestinationLng(),
                request.getDepartureTime(),
                request.getMaxPassengers(),
                request.getDescription(),
                request.isAutoAccept(),
                request.getStatus(),
                request.getPrice(),
                tags
        ));
        return PostDetailResponse.from(post, fetchNickname(post.getMemberId()), List.of());
    }

    @Transactional
    public void deletePost(Long id, Long requesterId) {
        Post post = postRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CarpoolException(ErrorCode.POST_NOT_FOUND));
        validateOwner(post, requesterId);

        List<Application> acceptedApps = applicationRepository.findByPostIdAndStatus(id, ApplicationStatus.ACCEPTED);
        List<Long> acceptedIds = acceptedApps.stream()
                .map(Application::getApplicantId)
                .collect(Collectors.toList());

        notificationService.saveAll(acceptedIds.stream()
                .map(aid -> Notification.ofPostCancelled(aid, id))
                .collect(Collectors.toList()));
        notificationPublisher.publishToMany(acceptedIds, NotificationPayload.builder()
                .type(NotificationType.POST_CANCELLED)
                .message("신청한 카풀 게시글이 취소되었습니다.")
                .data(Map.of("postId", id))
                .build());

        acceptedApps.forEach(Application::reject);

        List<Application> pendingApps = applicationRepository.findByPostIdAndStatus(id, ApplicationStatus.PENDING);
        pendingApps.forEach(Application::reject);

        post.delete();
    }

    private String fetchNickname(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getNickname)
                .orElse("알 수 없음");
    }

    private List<Tag> resolveTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();
        List<Tag> tags = tagRepository.findAllByIdIn(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new CarpoolException(ErrorCode.TAG_NOT_FOUND);
        }
        return tags;
    }

    private void validateOwner(Post post, Long requesterId) {
        if (!post.getMemberId().equals(requesterId)) {
            throw new CarpoolException(ErrorCode.POST_FORBIDDEN);
        }
    }
}
