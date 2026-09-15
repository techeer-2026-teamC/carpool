package com.techeer.carpool.domain.post;

import com.techeer.carpool.domain.post.entity.*;
import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PostCapacityTest {
    private Post post(PostType type, int capacity) {
        return Post.builder().memberId(1L).type(type).title("모아")
                .departureLocation("출발").departureLat(37.5).departureLng(127.0)
                .destinationLocation("도착").destinationLat(37.4).destinationLng(127.1)
                .departureTime(LocalDateTime.now().plusHours(1)).maxPassengers(capacity).price(3000).build();
    }

    @Test void taxiHostConsumesOneSeatAndPendingConsumesNone() {
        Post taxi = post(PostType.TAXI, 2);
        assertThat(taxi.getOccupiedSeats()).isEqualTo(1);
        assertThat(taxi.getAvailableSeats()).isEqualTo(1);
        taxi.incrementPassengers();
        assertThat(taxi.isFull()).isTrue();
        assertThat(taxi.getStatus()).isEqualTo(PostStatus.CLOSED);
        assertThatThrownBy(taxi::incrementPassengers).isInstanceOf(CarpoolException.class);
    }

    @Test void carpoolDriverDoesNotConsumePassengerCapacity() {
        Post carpool = post(PostType.CARPOOL, 1);
        assertThat(carpool.getOccupiedSeats()).isZero();
        carpool.incrementPassengers();
        assertThat(carpool.getOccupiedSeats()).isEqualTo(1);
    }

    @Test void cancellationReopensOnlyCapacityClosure() {
        Post autoClosed = post(PostType.CARPOOL, 1);
        autoClosed.incrementPassengers();
        autoClosed.decrementPassengers();
        assertThat(autoClosed.getStatus()).isEqualTo(PostStatus.OPEN);
        Post manuallyClosed = post(PostType.CARPOOL, 1);
        manuallyClosed.incrementPassengers();
        manuallyClosed.close();
        manuallyClosed.decrementPassengers();
        assertThat(manuallyClosed.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    @Test void departureOrMeetingCompletionPreventsMutation() {
        Post future = post(PostType.CARPOOL, 2);
        assertThatThrownBy(() -> future.requireBeforeCutoff(future.getDepartureTime())).isInstanceOf(CarpoolException.class);
        future.completeMeeting(LocalDateTime.now());
        assertThatThrownBy(() -> future.requireBeforeCutoff(LocalDateTime.now())).isInstanceOf(CarpoolException.class);
    }

    @Test void occupiedSeatsPreventCapacityReduction() {
        Post taxi = post(PostType.TAXI, 3);
        taxi.incrementPassengers();
        taxi.incrementPassengers();
        assertThatThrownBy(() -> taxi.validateUpdate(command(taxi, 2, taxi.getPrice()), PostType.TAXI, true))
                .isInstanceOfSatisfying(CarpoolException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POST_CAPACITY_INVALID));
    }

    @Test void firstApplicationFreezesPriceButAllowsDescriptionAndCapacity() {
        Post post = post(PostType.CARPOOL, 2);
        assertThatThrownBy(() -> post.validateUpdate(command(post, 3, 5000), PostType.CARPOOL, true))
                .isInstanceOfSatisfying(CarpoolException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECRUITMENT_FROZEN));
        PostUpdateCommand command = command(post, 3, 3000);
        post.validateUpdate(command, PostType.CARPOOL, true);
        post.updateFrom(command);
        assertThat(post.getCapacity()).isEqualTo(3);
    }

    private PostUpdateCommand command(Post p, int capacity, int price) {
        return new PostUpdateCommand(p.getTitle(), p.getDepartureLocation(), p.getDepartureLat(), p.getDepartureLng(),
                p.getDestinationLocation(), p.getDestinationLat(), p.getDestinationLng(), p.getDepartureTime(),
                capacity, "새 안내", false, null, price, List.of());
    }
}
