package com.restorant.service;

import com.restorant.config.RestaurantSettings;
import com.restorant.dto.Api;
import com.restorant.entity.Reservation;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.stereotype.Component;

/**
 * The restaurant's one cancellation rule, used for whole reservations and for single tables alike:
 * a table cancelled at least {@code cancellationHours} before the reservation starts is refundable in full;
 * a table cancelled later is not refundable. Customers cannot cancel online after the cutoff; staff still can.
 */
@Component
public class CancellationPolicy {
    private final RestaurantSettings settings;
    private final Clock clock;
    public CancellationPolicy(RestaurantSettings settings, Clock clock) { this.settings=settings; this.clock=clock; }
    public boolean beforeCutoff(LocalDateTime startsAt) {
        return !LocalDateTime.now(clock).plusHours(settings.cancellationHours()).isAfter(startsAt);
    }
    public boolean isActive(Reservation r) { return r.getStatus()==Reservation.Status.PENDING || r.getStatus()==Reservation.Status.CONFIRMED; }
    /** Whether the customer may still cancel the reservation, or some of its tables, online. */
    public boolean customerMayCancel(Reservation r) { return isActive(r) && beforeCutoff(r.getStartsAt()); }
    /** The part of a table's package price owed back when it is cancelled now. */
    public BigDecimal refundable(BigDecimal price, LocalDateTime startsAt) { return beforeCutoff(startsAt)?price:BigDecimal.ZERO; }
    public Api.CancellationTerms terms() {
        int hours=settings.cancellationHours();
        return new Api.CancellationTerms(hours,"Cancel a reservation, or individual tables, at least "+hours+" hour"+(hours==1?"":"s")
            +" before it starts and the package price of every cancelled table is refunded in full. Later cancellations are not refundable.");
    }
}
