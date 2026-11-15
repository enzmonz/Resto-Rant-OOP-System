package com.restorant.dto;
import com.restorant.entity.*;
import com.restorant.validation.PhMobile;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

public final class Api {
    private Api() {}
    public record Register(@NotBlank @Size(max=120) String fullName, @NotBlank @Email @Size(max=254) String email,
        @NotBlank @PhMobile String phoneNumber,
        @NotBlank @Size(min=10,max=72) String password, @NotBlank String confirmPassword) {}
    public record Login(@NotBlank @Email String email, @NotBlank @Size(max=72) String password) {}
    /**
     * A booking request. It carries no prices the server trusts: expectedTotal is only compared with the server's own
     * calculation so a customer is never charged a total different from the one they were shown.
     * requestId lets a retried request return the reservation it already created.
     */
    public record Booking(@NotNull LocalDate date, @NotNull LocalTime time, @Min(1) @Max(100) int guestCount,
        @NotBlank @PhMobile String contactNumber, @Size(max=1000) String specialRequests,
        @Size(max=25) List<@NotNull @Positive Long> tableIds, @Size(max=6) List<String> guestNeeds, boolean policyAccepted,
        @Min(60) @Max(180) Integer durationMinutes, @DecimalMin("0") BigDecimal expectedTotal, UUID requestId) {
        public Booking(LocalDate date,LocalTime time,int guests,String contact,String requests) { this(date,time,guests,contact,requests,List.of(),List.of(),true,null,null,null); }
        public Booking(LocalDate date,LocalTime time,int guests,String contact,String requests,List<Long> tableIds,List<String> guestNeeds,boolean policyAccepted) {
            this(date,time,guests,contact,requests,tableIds,guestNeeds,policyAccepted,null,null,null);
        }
        public Booking(LocalDate date,LocalTime time,int guests,String contact,String requests,List<Long> tableIds,List<String> guestNeeds,boolean policyAccepted,Integer durationMinutes) {
            this(date,time,guests,contact,requests,tableIds,guestNeeds,policyAccepted,durationMinutes,null,null);
        }
    }
    /** Tables to release from a multi-table reservation, with an optional reduced guest count. */
    public record CancelTables(@NotEmpty @Size(max=25) List<@NotNull @Positive Long> tableIds, @Min(1) @Max(100) Integer guestCount) {}
    public record PriceInput(@NotNull @DecimalMin("0.01") @DecimalMax("1000000") @Digits(integer=7,fraction=2) BigDecimal price) {}
    /** Approval is only accepted with verifiedAgainstAccount=true: staff attest they saw the money in the GCash account. */
    public record PaymentReview(boolean approved, boolean verifiedAgainstAccount, @Size(max=500) String note) {}
    public record RefundInput(@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount, @NotBlank @Size(max=500) String reason) {}
    public record RefundCompletion(@NotBlank @Size(max=150) String refundReference, @Size(max=500) String note) {}
    public record Note(@NotBlank @Size(max=500) String note) {}
    public record Profile(@NotBlank @Size(max=120) String fullName,@NotBlank @PhMobile String phoneNumber,boolean enabled) {}
    public record InquiryInput(@NotBlank @Size(max=120) String fullName,@NotBlank @Email @Size(max=254) String email,
        @NotBlank @PhMobile String phoneNumber,@NotBlank @Size(max=3000) String message) {}
    public record TableInput(@NotBlank @Size(max=30) String tableNumber, @Min(1) @Max(100) int capacity, boolean enabled) {}
    public record StatusInput(@NotNull Reservation.Status status) {}
    public record Account(Long id, String fullName, String email, String phoneNumber, String role, boolean canManageReservations, boolean enabled) {
        public static Account of(User u) {
            // POLYMORPHISM: The runtime account subtype supplies its capabilities to the client.
            return new Account(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getRole(), u.canManageReservations(),u.isEnabled());
        }
    }
    public record ItemView(Long id, Long tableId, String tableNumber, int capacity, BigDecimal price, ReservationItem.Status status,
        BigDecimal refundableAmount, Instant cancelledAt, String cancelledBy) {
        public static ItemView of(ReservationItem i) {
            return new ItemView(i.getId(),i.getTableId(),i.getTableNumber(),i.getCapacity(),i.getPrice(),i.getStatus(),i.getRefundableAmount(),i.getCancelledAt(),i.getCancelledBy());
        }
    }
    public record PaymentView(Long id, Long reservationId, String reservationReference, String customerName, String method, BigDecimal amount,
        Payment.Status status, String reference, String receivingAccount, boolean hasProof, Instant submittedAt, String submittedBy,
        Instant reviewedAt, String reviewedBy, String reviewNote) {
        public static PaymentView of(Payment p) {
            var r=p.getReservation();
            return new PaymentView(p.getId(),r.getId(),r.getReferenceNumber(),r.getCustomer().getFullName(),p.getMethod(),p.getAmount(),p.getStatus(),
                p.getReference(),p.getReceivingAccount(),p.hasProof(),p.getSubmittedAt(),p.getSubmittedBy(),p.getReviewedAt(),p.getReviewedBy(),p.getReviewNote());
        }
    }
    public record RefundView(Long id, Long reservationId, String reservationReference, String customerName, String customerPhone, Long paymentId,
        BigDecimal amount, String reason, String paymentReference, String refundReference, Refund.Status status,
        Instant createdAt, String createdBy, Instant completedAt, String completedBy, String note) {
        public static RefundView of(Refund f) {
            var r=f.getReservation();
            return new RefundView(f.getId(),r.getId(),r.getReferenceNumber(),r.getCustomer().getFullName(),r.getContactNumber(),f.getPayment().getId(),
                f.getAmount(),f.getReason(),f.getPaymentReference(),f.getRefundReference(),f.getStatus(),f.getCreatedAt(),f.getCreatedBy(),f.getCompletedAt(),f.getCompletedBy(),f.getNote());
        }
    }
    public record AuditView(Long id, String action, String detail, String actor, Instant at) {
        public static AuditView of(AuditEntry e) { return new AuditView(e.getId(),e.getAction(),e.getDetail(),e.getActor(),e.getCreatedAt()); }
    }
    /**
     * Money summary of one reservation. due: still to be paid. paid: verified payments. refundDue: owed back to the
     * customer under the cancellation policy and not yet covered by a refund. maxRefundable: hard ceiling for a new refund.
     */
    public record Money(BigDecimal originalTotal, BigDecimal currentTotal, BigDecimal due, BigDecimal paid, BigDecimal refunded,
        BigDecimal refundPending, BigDecimal refundDue, BigDecimal maxRefundable) {}
    public record BookingView(Long id, String referenceNumber, Long customerId, String customerName, String email,
        String contactNumber, Long tableId, String tableNumber, LocalDateTime startsAt, LocalDateTime endsAt, int durationMinutes,
        int guestCount, int seats, String specialRequests, Reservation.Status status, boolean cancellable, boolean tablesCancellable,
        List<Long> tableIds,String guestNeeds,List<ItemView> items,Reservation.PaymentStatus paymentStatus,Money money,
        List<PaymentView> payments,List<RefundView> refunds) {}
    public record TableView(Long id, String tableNumber, int capacity, boolean enabled, boolean available, BigDecimal price, boolean suggested) {}
    /** A bookable start time. The whole stay [time, endTime) fits before closing; availableTables is 0 when the party cannot be seated. */
    public record Slot(LocalTime time, LocalTime endTime, int availableTables,int availableSeats) {}
    public record PackageView(int capacity, BigDecimal price, boolean configured, long tables) {}
    public record PaymentInstructions(String provider, boolean automaticConfirmation, String gcashNumber, String gcashNumberDisplay, String accountName, List<String> steps) {}
    public record CancellationTerms(int cutoffHours, String summary) {}
    public record Pricing(List<PackageView> packages, PaymentInstructions payment, CancellationTerms cancellation) {}
}
