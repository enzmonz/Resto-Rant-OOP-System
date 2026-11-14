package com.restorant.dto;
import com.restorant.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

public final class Api {
    private Api() {}
    /** Philippine mobile number: 09XXXXXXXXX or +639XXXXXXXXX. */
    public static final String PHONE = "(09\\d{9}|\\+639\\d{9})";
    public static final String PHONE_MESSAGE = "Enter a Philippine mobile number such as 09171234567";
    public record Register(@NotBlank @Size(max=120) String fullName, @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Pattern(regexp=PHONE, message=PHONE_MESSAGE) String phoneNumber,
        @NotBlank @Size(min=10,max=72) String password, @NotBlank String confirmPassword) {}
    public record Login(@NotBlank @Email String email, @NotBlank @Size(max=72) String password) {}
    public record Booking(@NotNull LocalDate date, @NotNull LocalTime time, @Min(1) @Max(100) int guestCount,
        @NotBlank @Pattern(regexp=PHONE, message=PHONE_MESSAGE) String contactNumber, @Size(max=1000) String specialRequests,
        @Size(max=25) List<@NotNull @Positive Long> tableIds, @Size(max=6) List<String> guestNeeds, boolean depositAccepted,
        @Min(60) @Max(180) Integer durationMinutes) {
        public Booking(LocalDate date,LocalTime time,int guests,String contact,String requests) { this(date,time,guests,contact,requests,List.of(),List.of(),true,null); }
        public Booking(LocalDate date,LocalTime time,int guests,String contact,String requests,List<Long> tableIds,List<String> guestNeeds,boolean depositAccepted) {
            this(date,time,guests,contact,requests,tableIds,guestNeeds,depositAccepted,null);
        }
    }
    public record Payment(@NotBlank @Size(max=150) String reference) {}
    public record DemoCheckout(@NotNull @jakarta.validation.Valid Booking booking,@NotNull UUID checkoutId,
        @NotNull @DecimalMin("0.01") BigDecimal expectedAmount) {}
    public record DepositInput(@NotNull @DecimalMin("0.01") @DecimalMax("1000000") @Digits(integer=7,fraction=2) BigDecimal amount,@NotBlank @Size(max=1000) String method,boolean perPerson) {}
    public record PaymentReview(boolean accepted,@Size(max=500) String note) {}
    public record Profile(@NotBlank @Size(max=120) String fullName,@NotBlank @Pattern(regexp=PHONE, message=PHONE_MESSAGE) String phoneNumber,boolean enabled) {}
    public record InquiryInput(@NotBlank @Size(max=120) String fullName,@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Pattern(regexp=PHONE, message=PHONE_MESSAGE) String phoneNumber,@NotBlank @Size(max=3000) String message) {}
    public record TableInput(@NotBlank @Size(max=30) String tableNumber, @Min(1) @Max(100) int capacity, boolean enabled) {}
    public record StatusInput(@NotNull Reservation.Status status) {}
    public record Account(Long id, String fullName, String email, String phoneNumber, String role, boolean canManageReservations, boolean enabled) {
        public static Account of(User u) {
            // POLYMORPHISM: The runtime account subtype supplies its capabilities to the client.
            return new Account(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getRole(), u.canManageReservations(),u.isEnabled());
        }
    }
    public record BookingView(Long id, String referenceNumber, Long customerId, String customerName, String email,
        String contactNumber, Long tableId, String tableNumber, LocalDateTime startsAt, LocalDateTime endsAt,
        int guestCount, String specialRequests, Reservation.Status status, boolean cancellable,
        List<Long> tableIds,String guestNeeds,BigDecimal depositAmount,String depositStatus,String paymentReference,String paymentNote) {
        public static BookingView of(Reservation r, Clock clock, int cancellationHours) {
            boolean active = r.getStatus() == Reservation.Status.PENDING || r.getStatus() == Reservation.Status.CONFIRMED;
            return new BookingView(r.getId(), r.getReferenceNumber(), r.getCustomer().getId(), r.getCustomer().getFullName(),
                r.getCustomer().getEmail(), r.getContactNumber(), r.getTable().getId(), String.join(", ",r.getAssignedTables().stream().map(RestaurantTable::getTableNumber).toList()),
                r.getStartsAt(), r.getEndsAt(), r.getGuestCount(), r.getSpecialRequests(), r.getStatus(),
                active && !LocalDateTime.now(clock).plusHours(cancellationHours).isAfter(r.getStartsAt()),
                r.getAssignedTables().stream().map(RestaurantTable::getId).toList(),r.getGuestNeeds(),r.getDepositAmount(),r.getDepositStatus(),r.getPaymentReference(),r.getPaymentNote());
        }
    }
    public record TableView(Long id, String tableNumber, int capacity, boolean enabled, boolean available) {}
    public record Slot(LocalTime time, int availableTables,int availableSeats) {}
}
