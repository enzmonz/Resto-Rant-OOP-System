package com.restorant.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * A refund staff owe or have sent. The application never moves money: PENDING means "to be sent by hand in GCash",
 * COMPLETED means an administrator recorded the GCash reference of the transfer they made.
 */
@Entity @Table(name = "refunds")
public class Refund {
    public enum Status { PENDING, COMPLETED, VOIDED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id") private Reservation reservation;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "payment_id") private Payment payment;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false, length = 150) private String paymentReference;
    @Column(length = 150) private String refundReference;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 254) private String createdBy;
    private Instant completedAt;
    @Column(length = 254) private String completedBy;
    @Column(length = 500) private String note;
    @Version private long version;
    protected Refund() {}
    public Refund(Payment payment, BigDecimal amount, String reason, Instant now, String actor) {
        if (payment == null || !payment.isRefundable()) throw new IllegalStateException("Only a verified payment can be refunded");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) throw new IllegalArgumentException("Enter a refund amount above 0 with at most two decimals");
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("Enter the reason for this refund (up to 500 characters)");
        this.payment = payment; reservation = payment.getReservation(); this.amount = amount.setScale(2); this.reason = reason.trim();
        paymentReference = payment.getReference(); status = Status.PENDING; createdAt = now; createdBy = actor;
    }
    // ENCAPSULATION: A refund is closed exactly once, so it cannot be completed or counted twice.
    public void complete(String reference, String note, Instant now, String actor) {
        requirePending();
        if (reference == null || reference.isBlank() || reference.length() > 150) throw new IllegalArgumentException("Enter the GCash reference of the refund you sent");
        refundReference = reference; close(Status.COMPLETED, note, now, actor);
    }
    public void cancel(String note, Instant now, String actor) {
        requirePending();
        if (note == null || note.isBlank()) throw new IllegalArgumentException("Explain why this refund is being voided");
        close(Status.VOIDED, note, now, actor);
    }
    private void requirePending() {
        if (status != Status.PENDING) throw new IllegalStateException("This refund was already " + status.name().toLowerCase(Locale.ROOT));
    }
    private void close(Status outcome, String note, Instant now, String actor) {
        if (note != null && note.length() > 500) throw new IllegalArgumentException("Note exceeds 500 characters");
        status = outcome; this.note = note == null || note.isBlank() ? null : note.trim(); completedAt = now; completedBy = actor;
    }
    /** Pending and completed refunds both use up the refundable balance; voided ones give it back. */
    public boolean countsAgainstBalance() { return status != Status.VOIDED; }
    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Payment getPayment() { return payment; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public String getPaymentReference() { return paymentReference; }
    public String getRefundReference() { return refundReference; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCompletedBy() { return completedBy; }
    public String getNote() { return note; }
}
