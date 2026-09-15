package com.techeer.carpool.domain.discovery;

import com.techeer.carpool.domain.discovery.dto.*;
import com.techeer.carpool.global.exception.CarpoolException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class DiscoveryCursorTest {
    @Test void sameDepartureTimeUsesIdForCursor() {
        DiscoveryCursor cursor = new DiscoveryCursor(LocalDateTime.of(2026, 9, 15, 8, 0), 42);
        assertThat(DiscoveryCursor.decode(cursor.encode())).isEqualTo(cursor);
    }
    @Test void malformedCursorIsClientError() {
        assertThatThrownBy(() -> DiscoveryCursor.decode("not-a-cursor")).isInstanceOf(CarpoolException.class);
    }
    @Test void partialCircleAndNonFiniteCoordinatesAreRejected() {
        LocalDateTime from = LocalDateTime.now();
        assertThatThrownBy(() -> new DiscoveryRequest(null, from, from.plusHours(1), 37.0, null, 1000.0,
                null, null, null, 20, null).validate()).isInstanceOf(CarpoolException.class);
        assertThatThrownBy(() -> new DiscoveryRequest(null, from, from.plusHours(1), Double.NaN, 127.0, 1000.0,
                null, null, null, 20, null).validate()).isInstanceOf(CarpoolException.class);
    }
}
