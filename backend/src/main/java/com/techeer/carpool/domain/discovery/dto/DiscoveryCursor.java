package com.techeer.carpool.domain.discovery.dto;

import com.techeer.carpool.global.exception.CarpoolException;
import com.techeer.carpool.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public record DiscoveryCursor(LocalDateTime departureTime, long id) {
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((departureTime + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
    public static DiscoveryCursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() > 128) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            long id = Long.parseLong(parts[1]);
            if (id < 1) throw new IllegalArgumentException();
            return new DiscoveryCursor(LocalDateTime.parse(parts[0]), id);
        } catch (RuntimeException e) { throw new CarpoolException(ErrorCode.INVALID_INPUT); }
    }
}
