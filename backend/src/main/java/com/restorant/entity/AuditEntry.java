package com.restorant.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only history of pricing, payment, cancellation and refund actions. Rows are never updated or deleted. */
@Entity @Table(name = "payment_audit")
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long reservationId;
    private Long paymentId;
    private Long refundId;
    @Column(nullable = false, length = 40) private String action;
    @Column(nullable = false, length = 1000) private String detail;
    @Column(nullable = false, length = 254) private String actor;
    @Column(nullable = false) private Instant createdAt;
    protected AuditEntry() {}
    public AuditEntry(Long reservationId, Long paymentId, Long refundId, String action, String detail, String actor, Instant now) {
        this.reservationId = reservationId; this.paymentId = paymentId; this.refundId = refundId; this.action = action;
        this.detail = detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        this.actor = actor == null || actor.isBlank() ? "system" : actor; createdAt = now;
    }
    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public Long getPaymentId() { return paymentId; }
    public Long getRefundId() { return refundId; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public String getActor() { return actor; }
    public Instant getCreatedAt() { return createdAt; }
}
