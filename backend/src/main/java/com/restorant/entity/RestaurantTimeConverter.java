package com.restorant.entity;

import com.restorant.config.RestaurantSettings;
import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.*;
import org.springframework.stereotype.Component;

/**
 * Stores restaurant wall-clock times (Asia/Manila by default) as the UTC instant they denote.
 * Without this, Hibernate converts LocalDateTime through the JVM's default timezone, so a booking saved while the
 * computer was set to one timezone came back at a different hour after the timezone setting changed.
 * Rows written earlier on a machine set to the restaurant timezone already hold exactly this value.
 */
@Component @Converter
public class RestaurantTimeConverter implements AttributeConverter<LocalDateTime, Timestamp> {
    private final ZoneId zone;
    public RestaurantTimeConverter(RestaurantSettings settings) { zone = settings.zone(); }
    @Override public Timestamp convertToDatabaseColumn(LocalDateTime value) {
        return value == null ? null : Timestamp.from(value.atZone(zone).toInstant());
    }
    @Override public LocalDateTime convertToEntityAttribute(Timestamp value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), zone);
    }
}
