package com.restorant.dto;
import com.restorant.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;

public final class Api {
    private Api() {}
    public record Register(@NotBlank @Size(max=120) String fullName, @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Pattern(regexp="[+0-9() .-]{7,30}") String phoneNumber,
        @NotBlank @Size(min=10,max=72) String password, @NotBlank String confirmPassword) {}
    public record Login(@NotBlank @Email String email, @NotBlank @Size(max=72) String password) {}
    public record Booking(@NotNull LocalDate date, @NotNull LocalTime time, @Min(1) @Max(100) int guestCount,
        @NotBlank @Pattern(regexp="[+0-9() .-]{7,30}") String contactNumber, @Size(max=1000) String specialRequests) {}
    public record TableInput(@NotBlank @Size(max=30) String tableNumber, @Min(1) @Max(100) int capacity, boolean enabled) {}
    public record StatusInput(@NotNull Reservation.Status status) {}
    public record Account(Long id, String fullName, String email, String phoneNumber, String role, boolean canManageReservations) {
        public static Account of(User u) {
            // POLYMORPHISM: The runtime account subtype supplies its capabilities to the client.
            return new Account(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getRole(), u.canManageReservations());
        }
    }
    public record BookingView(Long id, String referenceNumber, Long customerId, String customerName, String email,
        String contactNumber, Long tableId, String tableNumber, LocalDateTime startsAt, LocalDateTime endsAt,
        int guestCount, String specialRequests, Reservation.Status status, boolean cancellable) {
        public static BookingView of(Reservation r, Clock clock, int cancellationHours) {
            boolean active = r.getStatus() == Reservation.Status.PENDING || r.getStatus() == Reservation.Status.CONFIRMED;
            return new BookingView(r.getId(), r.getReferenceNumber(), r.getCustomer().getId(), r.getCustomer().getFullName(),
                r.getCustomer().getEmail(), r.getContactNumber(), r.getTable().getId(), r.getTable().getTableNumber(),
                r.getStartsAt(), r.getEndsAt(), r.getGuestCount(), r.getSpecialRequests(), r.getStatus(),
                active && !LocalDateTime.now(clock).plusHours(cancellationHours).isAfter(r.getStartsAt()));
        }
    }
    public record TableView(Long id, String tableNumber, int capacity, boolean enabled, boolean available) {}
    public record Slot(LocalTime time, int availableTables) {}
}
