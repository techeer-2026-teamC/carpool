package com.techeer.carpool.global.init;

import com.techeer.carpool.domain.application.entity.Application;
import com.techeer.carpool.domain.application.repository.ApplicationRepository;
import com.techeer.carpool.domain.comment.entity.Comment;
import com.techeer.carpool.domain.comment.repository.CommentRepository;
import com.techeer.carpool.domain.driver.entity.Driver;
import com.techeer.carpool.domain.driver.repository.DriverRepository;
import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.post.entity.Post;
import com.techeer.carpool.domain.post.entity.PostStatus;
import com.techeer.carpool.domain.post.entity.Tag;
import com.techeer.carpool.domain.post.repository.PostRepository;
import com.techeer.carpool.domain.post.repository.TagRepository;
import com.techeer.carpool.domain.review.entity.Review;
import com.techeer.carpool.domain.review.repository.ReviewRepository;
import com.techeer.carpool.domain.ride.entity.Ride;
import com.techeer.carpool.domain.ride.entity.RidePassenger;
import com.techeer.carpool.domain.ride.entity.RideStatus;
import com.techeer.carpool.domain.ride.entity.PassengerStatus;
import com.techeer.carpool.domain.ride.repository.RidePassengerRepository;
import com.techeer.carpool.domain.ride.repository.RideRepository;
import com.techeer.carpool.domain.driver.entity.CarColor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class LocalDataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final ApplicationRepository applicationRepository;
    private final CommentRepository commentRepository;
    private final RideRepository rideRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final DriverRepository driverRepository;
    private final TagRepository tagRepository;

    @Override
    public void run(String... args) {
        Member test  = createMemberIfNotExists("test@carpool.com",  "password1234", "테스트유저");
        Member admin = createMemberIfNotExists("admin@carpool.com", "admin1234!",   "관리자");

        seedDrivers(test, admin);

        Map<String, Tag> tagMap = seedTags();

        List<Post> allPosts = postRepository.findByDeletedFalseWithTagsOrderByCreatedAtDesc();
        if (allPosts.isEmpty()) {
            allPosts = seedPosts(test, admin, tagMap);
        }

        if (rideRepository.count() == 0) {
            seedRides(test, admin, allPosts);
        }

        refreshTimeSensitiveData(allPosts);

        log.info("[LocalDataInitializer] 초기 데이터 생성 완료");
    }

    // ── Tags ───────────────────────────────────────────────────────────────────

    private Map<String, Tag> seedTags() {
        if (tagRepository.count() > 0) {
            return tagRepository.findAll().stream()
                    .collect(Collectors.toMap(Tag::getName, t -> t));
        }
        List<Tag> tags = tagRepository.saveAll(List.of(
                Tag.builder().name("금연").build(),
                Tag.builder().name("조용한 분위기").build(),
                Tag.builder().name("반려동물").build(),
                Tag.builder().name("짐 있음").build(),
                Tag.builder().name("여성전용").build(),
                Tag.builder().name("흡연").build()
        ));
        return tags.stream().collect(Collectors.toMap(Tag::getName, t -> t));
    }

    // ── Posts ──────────────────────────────────────────────────────────────────

    private List<Post> seedPosts(Member test, Member admin, Map<String, Tag> tagMap) {

        // ① OPEN — 신청 모집 중 (admin 드라이버)
        Post p1 = postRepository.save(Post.builder()
                .memberId(admin.getId())
                .title("강남역 → 판교역")
                .departureLocation("강남역")
                .departureLat(37.4979).departureLng(127.0276)
                .destinationLocation("판교역")
                .destinationLat(37.3943).destinationLng(127.1110)
                .departureTime(LocalDateTime.now().plusDays(1).withHour(8).withMinute(30).withSecond(0).withNano(0))
                .maxPassengers(3)
                .description("정시 출발합니다. 짐 많으신 분은 미리 말씀해 주세요.")
                .autoAccept(false)
                .price(5000)
                .tags(List.of(tagMap.get("금연"), tagMap.get("조용한 분위기")))
                .build());

        // ② OPEN — 신청 모집 중 (test 드라이버)
        Post p2 = postRepository.save(Post.builder()
                .memberId(test.getId())
                .title("홍대입구 → 여의도역")
                .departureLocation("홍대입구")
                .departureLat(37.5572).departureLng(126.9247)
                .destinationLocation("여의도역")
                .destinationLat(37.5215).destinationLng(126.9242)
                .departureTime(LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0))
                .maxPassengers(2)
                .description("경유지 없이 직행입니다.")
                .autoAccept(true)
                .price(3000)
                .tags(List.of(tagMap.get("반려동물"), tagMap.get("짐 있음")))
                .build());

        // ③ CLOSED → SCHEDULED 운행 (약 20분 후 출발 — 30분 전 위치 공유 테스트용, test 드라이버)
        Post p3 = postRepository.save(Post.builder()
                .memberId(test.getId())
                .title("서울역 → 수원역")
                .departureLocation("서울역")
                .departureLat(37.5547).departureLng(126.9707)
                .destinationLocation("수원역")
                .destinationLat(37.2664).destinationLng(127.0003)
                .departureTime(LocalDateTime.now().plusMinutes(20))
                .maxPassengers(4)
                .description("출발 20분 전 — 위치 공유 창 테스트용입니다.")
                .autoAccept(false)
                .price(4000)
                .tags(List.of(tagMap.get("금연")))
                .build());

        // ④ CLOSED → IN_PROGRESS 운행 중 (admin 드라이버, test 탑승 중)
        Post p4 = postRepository.save(Post.builder()
                .memberId(admin.getId())
                .title("잠실역 → 강남역")
                .departureLocation("잠실역")
                .departureLat(37.5133).departureLng(127.1001)
                .destinationLocation("강남역")
                .destinationLat(37.4979).destinationLng(127.0276)
                .departureTime(LocalDateTime.now().minusMinutes(25))
                .maxPassengers(3)
                .description("현재 운행 중 — ActiveRidePanel 테스트용")
                .autoAccept(true)
                .price(3500)
                .tags(List.of(tagMap.get("조용한 분위기")))
                .build());

        // ⑤ CLOSED → COMPLETED (test 드라이버, admin 탑승 완료 + 평점 등록됨)
        Post p5 = postRepository.save(Post.builder()
                .memberId(test.getId())
                .title("삼성역 → 잠실역")
                .departureLocation("삼성역")
                .departureLat(37.5087).departureLng(127.0632)
                .destinationLocation("잠실역")
                .destinationLat(37.5133).destinationLng(127.1001)
                .departureTime(LocalDateTime.now().minusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0))
                .maxPassengers(2)
                .description("과거 완료 운행 — 평점 등록 완료 케이스")
                .autoAccept(false)
                .price(2000)
                .build());

        // ⑥ CLOSED → COMPLETED (admin 드라이버, test 탑승 완료 + 평점 미등록)
        Post p6 = postRepository.save(Post.builder()
                .memberId(admin.getId())
                .title("신촌역 → 인천공항 T1")
                .departureLocation("신촌역")
                .departureLat(37.5551).departureLng(126.9368)
                .destinationLocation("인천공항 T1")
                .destinationLat(37.4491).destinationLng(126.4506)
                .departureTime(LocalDateTime.now().minusDays(1).withHour(5).withMinute(30).withSecond(0).withNano(0))
                .maxPassengers(3)
                .description("과거 완료 운행 — 평점 미등록 케이스 (★ 평가하기 버튼 표시)")
                .autoAccept(true)
                .price(15000)
                .tags(List.of(tagMap.get("짐 있음")))
                .build());

        // test 유저 소유 게시글 추가 (관리자가 신청할 대상)
        Post p7 = postRepository.save(Post.builder()
                .memberId(test.getId())
                .title("잠실역 → 강남역")
                .departureLocation("잠실역")
                .departureLat(37.5134).departureLng(127.1000)
                .destinationLocation("강남역")
                .destinationLat(37.4979).destinationLng(127.0276)
                .departureTime(LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0))
                .maxPassengers(3)
                .description("퇴근길 카풀입니다. 편하게 연락 주세요.")
                .autoAccept(false)
                .price(3000)
                .build());

        Post p8 = postRepository.save(Post.builder()
                .memberId(test.getId())
                .title("신촌역 → 인천공항")
                .departureLocation("신촌역")
                .departureLat(37.5551).departureLng(126.9368)
                .destinationLocation("인천공항 T1")
                .destinationLat(37.4494).destinationLng(126.4508)
                .departureTime(LocalDateTime.now().plusDays(4).withHour(5).withMinute(30).withSecond(0).withNano(0))
                .maxPassengers(2)
                .description("새벽 출발이라 짐 많으신 분도 환영합니다.")
                .autoAccept(false)
                .price(20000)
                .build());

        seedApplications(test, admin, p2, p7, p8);

        seedComments(p1, test, admin);
        seedComments(p2, admin, test);

        return List.of(p1, p2, p3, p4, p5, p6, p7, p8);
    }

    private void seedApplications(Member test, Member admin, Post p2, Post p7, Post p8) {
        // 관리자 → p2 (홍대→여의도): PENDING — 테스트유저가 아직 처리 안 한 신청
        applicationRepository.save(Application.builder()
                .postId(p2.getId())
                .applicantId(admin.getId())
                .build());

        // 관리자 → p7 (잠실→강남, test 소유): ACCEPTED — 수락된 신청, currentPassengers 반영
        Application acceptedApp = applicationRepository.save(Application.builder()
                .postId(p7.getId())
                .applicantId(admin.getId())
                .build());
        acceptedApp.accept();
        applicationRepository.save(acceptedApp);
        p7.incrementPassengers();
        postRepository.save(p7);

        // 관리자 → p8 (신촌→인천공항, test 소유): REJECTED — 거절된 신청
        Application rejectedApp = applicationRepository.save(Application.builder()
                .postId(p8.getId())
                .applicantId(admin.getId())
                .build());
        rejectedApp.reject();
        applicationRepository.save(rejectedApp);
    }

    private void seedComments(Post post, Member first, Member second) {
        commentRepository.save(Comment.builder()
                .postId(post.getId()).memberId(first.getId())
                .content("저도 같은 방향이에요! 탑승 가능할까요?").build());
        commentRepository.save(Comment.builder()
                .postId(post.getId()).memberId(second.getId())
                .content("몇 시쯤 도착 예정인가요?").build());
        commentRepository.save(Comment.builder()
                .postId(post.getId()).memberId(first.getId())
                .content("좋아요, 당일 아침에 다시 연락 드릴게요 :)").build());
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    private void refreshTimeSensitiveData(List<Post> posts) {
        // 서울역 → 수원역: 앱 재시작 시마다 출발 시각을 지금으로부터 20분 후로 갱신
        posts.stream()
                .filter(p -> "서울역 → 수원역".equals(p.getTitle()))
                .findFirst()
                .ifPresent(p -> {
                    p.refreshDepartureTime(LocalDateTime.now().plusMinutes(20));
                    postRepository.save(p);
                });

        // IN_PROGRESS 운행: startedAt·boardedAt을 지금 기준으로 갱신
        rideRepository.findAll().stream()
                .filter(r -> r.getStatus() == RideStatus.IN_PROGRESS)
                .findFirst()
                .ifPresent(r -> {
                    r.refreshStartedAt(LocalDateTime.now().minusMinutes(20));
                    rideRepository.save(r);
                    ridePassengerRepository.findByRideId(r.getId())
                            .forEach(rp -> {
                                rp.refreshBoardedAt(LocalDateTime.now().minusMinutes(18));
                                ridePassengerRepository.save(rp);
                            });
                });
    }

    // ── Rides ──────────────────────────────────────────────────────────────────

    private void seedRides(Member test, Member admin, List<Post> posts) {
        Post p3 = findByTitle(posts, "서울역 → 수원역");      // test 드라이버, 20분 후 출발
        Post p4 = findByTitle(posts, "잠실역 → 강남역");      // admin 드라이버, 운행 중
        Post p5 = findByTitle(posts, "삼성역 → 잠실역");      // test 드라이버, 완료+평점 있음
        Post p6 = findByTitle(posts, "신촌역 → 인천공항 T1"); // admin 드라이버, 완료+평점 없음

        if (p3 == null || p4 == null || p5 == null || p6 == null) return;

        // ─── SCHEDULED (출발 20분 전) ───
        // test가 드라이버, admin이 탑승 예정 → SCHEDULED → 위치 공유 창 표시
        closePost(p3);
        Application appA = saveAcceptedApplication(p3.getId(), admin.getId());
        Ride ride1 = rideRepository.save(Ride.builder()
                .postId(p3.getId())
                .driverId(test.getId())
                .build()); // status 기본값 SCHEDULED
        ridePassengerRepository.save(RidePassenger.builder()
                .ride(ride1)
                .applicationId(appA.getId())
                .passengerId(admin.getId())
                .build()); // status 기본값 PENDING

        // ─── IN_PROGRESS (운행 중) ───
        // admin이 드라이버, test가 탑승 중 → ActiveRidePanel (승객) 표시
        closePost(p4);
        Application appB = saveAcceptedApplication(p4.getId(), test.getId());
        Ride ride2 = rideRepository.save(Ride.builder()
                .postId(p4.getId())
                .driverId(admin.getId())
                .status(RideStatus.IN_PROGRESS)
                .currentLatitude(37.5133)
                .currentLongitude(127.0700)
                .startedAt(LocalDateTime.now().minusMinutes(20))
                .build());
        ridePassengerRepository.save(RidePassenger.builder()
                .ride(ride2)
                .applicationId(appB.getId())
                .passengerId(test.getId())
                .status(PassengerStatus.BOARDED)
                .boardedAt(LocalDateTime.now().minusMinutes(18))
                .build());

        // ─── COMPLETED + 평점 등록 완료 ───
        // test가 드라이버, admin이 탑승 완료 + 이미 평점 남김
        closePost(p5);
        Application appC = saveAcceptedApplication(p5.getId(), admin.getId());
        Ride ride3 = rideRepository.save(Ride.builder()
                .postId(p5.getId())
                .driverId(test.getId())
                .status(RideStatus.COMPLETED)
                .startedAt(LocalDateTime.now().minusDays(3).withHour(9).withMinute(0))
                .completedAt(LocalDateTime.now().minusDays(3).withHour(9).withMinute(45))
                .build());
        ridePassengerRepository.save(RidePassenger.builder()
                .ride(ride3)
                .applicationId(appC.getId())
                .passengerId(admin.getId())
                .status(PassengerStatus.DROPPED_OFF)
                .boardedAt(LocalDateTime.now().minusDays(3).withHour(9).withMinute(3))
                .droppedOffAt(LocalDateTime.now().minusDays(3).withHour(9).withMinute(45))
                .build());
        // admin이 test 드라이버에게 평점 등록 (★★★★★ — ReviewModal "평가 완료" 표시 확인)
        reviewRepository.save(Review.builder()
                .rideId(ride3.getId())
                .reviewerId(admin.getId())
                .driverId(test.getId())
                .rating(5)
                .comment("운전이 안전하고 친절했어요! 다음에도 이용하고 싶습니다.")
                .build());
        test.addRating(5);
        memberRepository.save(test);

        // ─── COMPLETED + 평점 미등록 ───
        // admin이 드라이버, test가 탑승 완료 + 평점 안 남김 → "★ 평가하기" 버튼 표시
        closePost(p6);
        Application appD = saveAcceptedApplication(p6.getId(), test.getId());
        Ride ride4 = rideRepository.save(Ride.builder()
                .postId(p6.getId())
                .driverId(admin.getId())
                .status(RideStatus.COMPLETED)
                .startedAt(LocalDateTime.now().minusDays(1).withHour(5).withMinute(30))
                .completedAt(LocalDateTime.now().minusDays(1).withHour(6).withMinute(50))
                .build());
        ridePassengerRepository.save(RidePassenger.builder()
                .ride(ride4)
                .applicationId(appD.getId())
                .passengerId(test.getId())
                .status(PassengerStatus.DROPPED_OFF)
                .boardedAt(LocalDateTime.now().minusDays(1).withHour(5).withMinute(33))
                .droppedOffAt(LocalDateTime.now().minusDays(1).withHour(6).withMinute(50))
                .build());
        // 평점 미등록 — test 로그인 시 "★ 드라이버 평가하기" 버튼이 ride4에 표시됨
    }

    private void closePost(Post post) {
        try { post.close(); postRepository.save(post); }
        catch (Exception ignored) {} // 이미 마감된 경우 무시
    }

    private Application saveAcceptedApplication(Long postId, Long applicantId) {
        Application app = applicationRepository.save(
                Application.builder().postId(postId).applicantId(applicantId).build());
        app.accept();
        return applicationRepository.save(app);
    }

    private Post findByTitle(List<Post> posts, String title) {
        return posts.stream().filter(p -> title.equals(p.getTitle())).findFirst().orElse(null);
    }

    // ── Members ────────────────────────────────────────────────────────────────

    private Member createMemberIfNotExists(String email, String password, String nickname) {
        return memberRepository.findByEmail(email).orElseGet(() ->
                memberRepository.save(Member.builder()
                        .email(email)
                        .password(passwordEncoder.encode(password))
                        .nickname(nickname)
                        .build()));
    }

    // ── Drivers ────────────────────────────────────────────────────────────────

    private void seedDrivers(Member test, Member admin) {
        if (driverRepository.count() > 0) return;

        driverRepository.save(Driver.builder()
                .memberId(test.getId())
                .carModel("아반떼")
                .carColor(CarColor.WHITE)
                .carNumber("12가3456")
                .build());

        driverRepository.save(Driver.builder()
                .memberId(admin.getId())
                .carModel("K5")
                .carColor(CarColor.BLACK)
                .carNumber("99나8765")
                .build());
    }
}
