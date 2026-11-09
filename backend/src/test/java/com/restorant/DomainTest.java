package com.restorant;

import com.restorant.entity.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DomainTest {
    private final Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final String hash = "$2a$10$" + "a".repeat(53);
    private Customer customer() { return new Customer("Alex", "ALEX@example.com", "09123456789", hash); }
    private Reservation reservation() {
        return new Reservation(customer(), new RestaurantTable("T1", 4),
                LocalDateTime.of(2030, 1, 2, 12, 0), LocalDateTime.of(2030, 1, 2, 14, 0), 4, null, clock);
    }
    @Test void rolesUseRuntimePolymorphism() {
        User customer = customer();
        User admin = new Admin("Manager", "admin@example.com", "09123456789", hash);
        assertThat(customer.canManageReservations()).isFalse();
        assertThat(admin.canManageReservations()).isTrue();
        assertThat(customer.getEmail()).isEqualTo("alex@example.com");
    }
    @Test void failedTableUpdatePreservesState() {
        var table = new RestaurantTable("T1", 4);
        assertThatThrownBy(() -> table.update("T2", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(table.getTableNumber()).isEqualTo("T1");
        table.setEnabled(false);
        assertThat(table.accommodates(2)).isFalse();
    }
    @Test void adjacentReservationsDoNotOverlapAndCancellationReleasesOccupancy() {
        var reservation = reservation();
        assertThat(reservation.overlaps(reservation.getStartsAt(), reservation.getEndsAt())).isTrue();
        assertThat(reservation.overlaps(reservation.getEndsAt(), reservation.getEndsAt().plusHours(2))).isFalse();
        reservation.cancel();
        assertThat(reservation.overlaps(reservation.getStartsAt(), reservation.getEndsAt())).isFalse();
        assertThatThrownBy(reservation::confirm).isInstanceOf(IllegalStateException.class);
    }
    @Test void completionRequiresConfirmedAndEndedReservation() {
        var reservation = reservation();
        reservation.confirm();
        assertThatThrownBy(() -> reservation.complete(clock)).isInstanceOf(IllegalStateException.class);
        reservation.complete(Clock.offset(clock, Duration.ofDays(3)));
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.COMPLETED);
        assertThatThrownBy(reservation::cancel).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsPastReservationAndUndersizedTable() {
        assertThatThrownBy(() -> new Reservation(customer(), new RestaurantTable("T1", 2),
                LocalDateTime.now(clock).plusDays(1), LocalDateTime.now(clock).plusDays(1).plusHours(2), 3, null, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Reservation(customer(), new RestaurantTable("T1", 2),
                LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock), 2, null, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
