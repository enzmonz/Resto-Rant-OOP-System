package com.restorant.entity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "reservations")
public class Reservation {
    public enum Status { PENDING, CONFIRMED, CANCELLED, COMPLETED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 36) private String referenceNumber;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "table_id") private RestaurantTable table;
    @Column(nullable = false) private LocalDateTime startsAt;
    @Column(nullable = false) private LocalDateTime endsAt;
    @Column(nullable = false) private int guestCount;
    @Column(length = 1000) private String specialRequests;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, length = 30) private String contactNumber;
    @Column(nullable = false) private Instant createdAt;
    @Version private long version;
    protected Reservation() {}
    public Reservation(Customer customer, RestaurantTable table, LocalDateTime start, LocalDateTime end,
                       int guests, String requests, Clock clock) {
        if (customer == null || table == null || !table.accommodates(guests))
            throw new IllegalArgumentException("Customer and a table with sufficient capacity are required");
        if (start == null || end == null || !start.isAfter(LocalDateTime.now(clock)) || !end.isAfter(start))
            throw new IllegalArgumentException("Reservation must be in the future with a positive duration");
        if (requests != null && requests.length() > 1000) throw new IllegalArgumentException("Requests exceed 1000 characters");
        this.customer = customer; this.table = table; startsAt = start; endsAt = end;
        guestCount = guests; specialRequests = requests; createdAt = clock.instant();
        referenceNumber = UUID.randomUUID().toString(); status = Status.PENDING;
        contactNumber = customer.getPhoneNumber();
    }
    // ENCAPSULATION: Terminal states cannot be reopened through a generic status setter.
    public void confirm() {
        if (status != Status.PENDING) throw new IllegalStateException("Only pending reservations can be confirmed");
        status = Status.CONFIRMED;
    }
    public void cancel() {
        if (status != Status.PENDING && status != Status.CONFIRMED) throw new IllegalStateException("Reservation cannot be cancelled");
        status = Status.CANCELLED;
    }
    public void complete(Clock clock) {
        if (status != Status.CONFIRMED || LocalDateTime.now(clock).isBefore(endsAt))
            throw new IllegalStateException("Only ended, confirmed reservations can be completed");
        status = Status.COMPLETED;
    }
    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        return (status == Status.PENDING || status == Status.CONFIRMED) && startsAt.isBefore(end) && endsAt.isAfter(start);
    }
    public Long getId() { return id; }
    public void setContactNumber(String phone) {
        if (phone == null || !phone.matches("[+0-9() .-]{7,30}")) throw new IllegalArgumentException("A valid contact number is required");
        contactNumber = phone;
    }
    public String getContactNumber() { return contactNumber; }
    public String getReferenceNumber() { return referenceNumber; }
    public Customer getCustomer() { return customer; }
    public RestaurantTable getTable() { return table; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public int getGuestCount() { return guestCount; }
    public String getSpecialRequests() { return specialRequests; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
