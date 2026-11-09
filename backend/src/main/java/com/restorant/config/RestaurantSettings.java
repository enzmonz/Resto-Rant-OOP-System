package com.restorant.config;

import java.time.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "restaurant")
public record RestaurantSettings(ZoneId zone, LocalTime opening, LocalTime closing,
                                int durationMinutes, int slotMinutes, int maxGuests, int cancellationHours) {
    public RestaurantSettings {
        if (zone == null || opening == null || closing == null || !closing.isAfter(opening)
                || durationMinutes < 1 || durationMinutes > Duration.between(opening, closing).toMinutes()
                || slotMinutes < 1 || maxGuests < 1 || cancellationHours < 0)
            throw new IllegalArgumentException("Invalid restaurant operating settings");
    }
}
