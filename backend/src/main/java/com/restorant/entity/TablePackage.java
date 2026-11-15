package com.restorant.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** The reservation package price for every table with a given number of seats. */
@Entity @Table(name = "table_packages")
public class TablePackage {
    public static final BigDecimal MAX_PRICE = new BigDecimal("1000000");
    @Id private Integer capacity;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    private Instant updatedAt;
    @Column(length = 254) private String updatedBy;
    protected TablePackage() {}
    public TablePackage(int capacity, BigDecimal price, Instant now, String actor) {
        if (capacity < 1 || capacity > 100) throw new IllegalArgumentException("Seating capacity must be 1 to 100");
        this.capacity = capacity; reprice(price, now, actor);
    }
    // ENCAPSULATION: A price is validated before it replaces the current one.
    public void reprice(BigDecimal price, Instant now, String actor) {
        if (price == null || price.signum() <= 0 || price.scale() > 2 || price.compareTo(MAX_PRICE) > 0)
            throw new IllegalArgumentException("Enter a package price above 0 and up to 1,000,000 with at most two decimals");
        this.price = price.setScale(2); updatedAt = now; updatedBy = actor;
    }
    public int getCapacity() { return capacity; }
    public BigDecimal getPrice() { return price; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
