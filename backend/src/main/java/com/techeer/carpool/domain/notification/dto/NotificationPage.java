package com.techeer.carpool.domain.notification.dto;

import java.util.List;

public record NotificationPage(List<NotificationPayload> items, Long nextCursor, boolean hasNext) {
}
