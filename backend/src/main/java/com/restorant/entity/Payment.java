package com.restorant.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * A customer's claim that they paid for a reservation. A claim is never proof: it stays PENDING until staff
 * check the receiving GCash account and mark it PAID or REJECTED.
 */
@Entity @Table(name = "payments")
public class Payment {
    public enum Status { PENDING, PAID, REJECTED }
    /** A transfer the customer makes in their own GCash app; verified by staff. */
    public static final String GCASH_MANUAL = "GCASH_MANUAL";
    /** Records migrated from the earlier classroom simulator. No money was ever received for these. */
    public static final String DEMO_SIMULATED = "DEMO_SIMULATED";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id") private Reservation reservation;
    @Column(nullable = false, length = 30) private String method;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, length = 150) private String reference;
    @Column(length = 30) private String receivingAccount;
    @Column(nullable = false) private boolean hasProof;
    @Column(nullable = false) private Instant submittedAt;
    @Column(nullable = false, length = 254) private String submittedBy;
    private Instant reviewedAt;
    @Column(length = 254) private String reviewedBy;
    @Column(length = 500) private String reviewNote;
    @Version private long version;
    protected Payment() {}
    public Payment(Reservation reservation, BigDecimal amount, String reference, String receivingAccount, boolean hasProof, Instant now, String actor) {
        if (amount == null || amount.signum() <= 0) throw new IllegalStateException("There is no amount due for this reservation");
        this.reservation = reservation; this.amount = amount; this.reference = reference; this.receivingAccount = receivingAccount;
        this.hasProof = hasProof; method = GCASH_MANUAL; status = Status.PENDING; submittedAt = now; submittedBy = actor;
    }
    // ENCAPSULATION: A payment is decided exactly once; PAID and REJECTED are final.
    public void review(boolean approved, String note, Instant now, String actor) {
        if (status != Status.PENDING) throw new IllegalStateException("This payment was already " + status.name().toLowerCase(Locale.ROOT));
        if (note != null && note.length() > 500) throw new IllegalArgumentException("Note exceeds 500 characters");
        if (!approved && (note == null || note.isBlank())) throw new IllegalArgumentException("Explain why the payment is rejected so the customer can fix it");
        status = approved ? Status.PAID : Status.REJECTED; reviewNote = note == null || note.isBlank() ? null : note.trim();
        reviewedAt = now; reviewedBy = actor;
    }
    /** Only money that staff verified in the receiving account can ever be refunded. */
    public boolean isRefundable() { return status == Status.PAID && !DEMO_SIMULATED.equals(method); }
    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public String getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getReference() { return reference; }
    public String getReceivingAccount() { return receivingAccount; }
    public boolean hasProof() { return hasProof; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getSubmittedBy() { return submittedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewNote() { return reviewNote; }
}
