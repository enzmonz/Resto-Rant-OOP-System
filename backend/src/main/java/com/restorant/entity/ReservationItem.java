package com.restorant.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** One reserved table and the package price that applied when it was booked (a price snapshot). */
@Entity @Table(name = "reservation_items")
public class ReservationItem {
    /** ACTIVE: held for the guest. CANCELLED: released by a cancellation. REMOVED: swapped out by a staff edit before payment. */
    public enum Status { ACTIVE, CANCELLED, REMOVED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id") private Reservation reservation;
    @Column(name = "table_id", nullable = false) private Long tableId;
    @Column(nullable = false, length = 30) private String tableNumber;
    @Column(nullable = false) private int capacity;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 20) private Status status = Status.ACTIVE;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal refundableAmount = BigDecimal.ZERO.setScale(2);
    private Instant cancelledAt;
    @Column(length = 254) private String cancelledBy;
    protected ReservationItem() {}
    ReservationItem(Reservation reservation, RestaurantTable table, BigDecimal price) {
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("A table package price is required");
        this.reservation = reservation; tableId = table.getId(); tableNumber = table.getTableNumber();
        capacity = table.getCapacity(); this.price = price.setScale(2, RoundingMode.HALF_UP);
    }
    void release(Status outcome, BigDecimal refundable, Instant at, String actor) {
        if (status != Status.ACTIVE) throw new IllegalStateException("Table " + tableNumber + " is no longer part of this reservation");
        if (refundable == null || refundable.signum() < 0 || refundable.compareTo(price) > 0)
            throw new IllegalArgumentException("A refundable amount cannot exceed the table's package price");
        status = outcome; refundableAmount = refundable.setScale(2, RoundingMode.HALF_UP); cancelledAt = at; cancelledBy = actor;
    }
    public boolean isActive() { return status == Status.ACTIVE; }
    public Long getId() { return id; }
    public Long getTableId() { return tableId; }
    public String getTableNumber() { return tableNumber; }
    public int getCapacity() { return capacity; }
    public BigDecimal getPrice() { return price; }
    public Status getStatus() { return status; }
    public BigDecimal getRefundableAmount() { return refundableAmount; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelledBy() { return cancelledBy; }
}
