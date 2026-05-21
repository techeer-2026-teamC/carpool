package com.techeer.carpool.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CarpoolMetrics {

    private final Counter applicationSubmittedCounter;
    private final Counter applicationAcceptedCounter;
    private final Counter applicationRejectedCounter;
    private final Counter postCreatedCounter;
    private final Counter postClosedCounter;
    private final Counter rideStartedCounter;
    private final Counter rideCompletedCounter;
    private final Counter optimisticLockConflictCounter;
    private final Counter reviewCreatedCounter;
    private final Counter locationUpdatedCounter;

    public CarpoolMetrics(MeterRegistry registry) {
        applicationSubmittedCounter = Counter.builder("carpool.applications.submitted")
                .description("카풀 신청 제출 횟수").register(registry);
        applicationAcceptedCounter = Counter.builder("carpool.applications.accepted")
                .description("카풀 신청 수락 횟수").register(registry);
        applicationRejectedCounter = Counter.builder("carpool.applications.rejected")
                .description("카풀 신청 거절 횟수").register(registry);
        postCreatedCounter = Counter.builder("carpool.posts.created")
                .description("게시글 생성 횟수").register(registry);
        postClosedCounter = Counter.builder("carpool.posts.closed")
                .description("게시글 마감 횟수").register(registry);
        rideStartedCounter = Counter.builder("carpool.rides.started")
                .description("라이드 시작 횟수").register(registry);
        rideCompletedCounter = Counter.builder("carpool.rides.completed")
                .description("라이드 완료 횟수").register(registry);
        optimisticLockConflictCounter = Counter.builder("carpool.optimistic_lock.conflicts")
                .description("동시 신청으로 인한 Optimistic Lock 충돌 횟수").register(registry);
        reviewCreatedCounter = Counter.builder("carpool.reviews.created")
                .description("리뷰 생성 횟수").register(registry);
        locationUpdatedCounter = Counter.builder("carpool.location.updates")
                .description("드라이버 위치 업데이트 횟수").register(registry);
    }

    public void incrementApplicationSubmitted() { applicationSubmittedCounter.increment(); }
    public void incrementApplicationAccepted() { applicationAcceptedCounter.increment(); }
    public void incrementApplicationRejected() { applicationRejectedCounter.increment(); }
    public void incrementPostCreated() { postCreatedCounter.increment(); }
    public void incrementPostClosed() { postClosedCounter.increment(); }
    public void incrementRideStarted() { rideStartedCounter.increment(); }
    public void incrementRideCompleted() { rideCompletedCounter.increment(); }
    public void incrementOptimisticLockConflict() { optimisticLockConflictCounter.increment(); }
    public void incrementReviewCreated() { reviewCreatedCounter.increment(); }
    public void incrementLocationUpdated() { locationUpdatedCounter.increment(); }
}
