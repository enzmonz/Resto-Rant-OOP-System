package com.restorant.entity;
import com.restorant.validation.PhoneNumbers;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

@Entity @Table(name = "reservations")
public class Reservation {
    public enum Status { PENDING, CONFIRMED, CANCELLED, COMPLETED }
    /**
     * Where the reservation's package payment stands. UNPAID: nothing submitted. PENDING: the customer submitted a
     * GCash reference that staff have not verified. PAID: staff verified the money in the receiving account.
     * REJECTED: staff could not find the payment; the customer may submit again.
     */
    public enum PaymentStatus { NOT_REQUIRED, UNPAID, PENDING, PAID, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 36) private String referenceNumber;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "table_id") private RestaurantTable table;
    // Restaurant wall-clock times; the converter keeps them independent of the server's timezone setting.
    @Convert(converter = RestaurantTimeConverter.class) @Column(nullable = false) private LocalDateTime startsAt;
    @Convert(converter = RestaurantTimeConverter.class) @Column(nullable = false) private LocalDateTime endsAt;
    @Column(nullable = false) private int guestCount;
    @Column(length = 1000) private String specialRequests;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, length = 30) private String contactNumber;
    @Column(nullable = false) private Instant createdAt;
    @Version private long version;
    /** Tables this booking currently occupies. Cancelled tables leave this set; their history stays in {@link #items}. */
    @ManyToMany
    @JoinTable(name="reservation_tables", joinColumns=@JoinColumn(name="reservation_id"), inverseJoinColumns=@JoinColumn(name="table_id"))
    private Set<RestaurantTable> assignedTables = new LinkedHashSet<>();
    /** Price snapshot: one line per table ever booked, with the package price that applied at the time. */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL) @OrderBy("id")
    private List<ReservationItem> items = new ArrayList<>();
    @Column(nullable=false,length=500) private String guestNeeds = "";
    /** Package total recorded when the booking was made. It is kept as history and never recalculated from later prices. */
    @Column(name = "deposit_amount", nullable=false,precision=12,scale=2) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "deposit_status", nullable=false,length=25) private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;
    @Column(length = 36, unique = true) private String clientRequestId;
    protected Reservation() {}
    public Reservation(Customer customer, RestaurantTable table, LocalDateTime start, LocalDateTime end,
                       int guests, String requests, Clock clock) {
        this(customer, List.of(table), start, end, guests, requests, clock);
    }
    public Reservation(Customer customer, List<RestaurantTable> tables, LocalDateTime start, LocalDateTime end,
                       int guests, String requests, Clock clock) {
        if (customer == null || tables == null) throw new IllegalArgumentException("A customer and at least one table are required");
        if (start == null || !start.isAfter(LocalDateTime.now(clock))) throw new IllegalArgumentException("Reservation must be in the future with a positive duration");
        this.customer = customer; createdAt = clock.instant();
        referenceNumber = UUID.randomUUID().toString(); status = Status.PENDING;
        contactNumber = customer.getPhoneNumber();
        assign(tables, guests, start, end, requests);
    }
    // ENCAPSULATION: A booking owns a validated group of tables; callers cannot mutate that group directly.
    // The seats of the tables actually assigned must cover the party: restaurant-wide capacity is never enough.
    private void assign(List<RestaurantTable> tables,int guests,LocalDateTime start,LocalDateTime end,String requests) {
        if (tables.isEmpty() || new HashSet<>(tables).size()!=tables.size() || tables.stream().anyMatch(t -> !t.isEnabled())
            || guests<1 || tables.stream().mapToInt(RestaurantTable::getCapacity).sum()<guests)
            throw new IllegalArgumentException("Select enough distinct, enabled tables to seat every guest in your party");
        if(start==null || end==null || !end.isAfter(start) || (requests!=null && requests.length()>1000)) throw new IllegalArgumentException("Invalid reservation details");
        requireActive();
        assignedTables.clear();assignedTables.addAll(tables);table=tables.getFirst();guestCount=guests;startsAt=start;endsAt=end;specialRequests=requests;
    }
    private void requireActive() {
        if(status==Status.CANCELLED || status==Status.COMPLETED) throw new IllegalStateException("Cancelled and completed reservations cannot be changed");
    }
    /** Records the package price of every assigned table. The prices come from the server's price list, never from the client. */
    public void priceItems(Function<RestaurantTable,BigDecimal> priceOf) {
        if(!items.isEmpty()) throw new IllegalStateException("Prices were already recorded for this reservation");
        for(var t:assignedTables) items.add(new ReservationItem(this,t,priceOf.apply(t)));
        totalAmount=activeTotal();paymentStatus=totalAmount.signum()>0?PaymentStatus.UNPAID:PaymentStatus.NOT_REQUIRED;
    }
    /** Staff edit. Tables can only be swapped before a payment is submitted, so a paid price is never silently changed. */
    public void reassign(List<RestaurantTable> tables,int guests,LocalDateTime start,LocalDateTime end,String requests,
                         Function<RestaurantTable,BigDecimal> priceOf,Instant now,String actor) {
        var current=new HashSet<Long>();for(var t:assignedTables)current.add(t.getId());
        var wanted=new HashSet<Long>();for(var t:tables)wanted.add(t.getId());
        boolean changed=!current.equals(wanted);
        if(changed && (paymentStatus==PaymentStatus.PENDING || paymentStatus==PaymentStatus.PAID))
            throw new IllegalStateException("A payment is on file for these tables. Cancel the tables that are no longer needed and book the new ones separately so payment and refund records stay accurate.");
        assign(tables,guests,start,end,requests);
        if(!changed)return;
        for(var item:activeItems())if(!wanted.contains(item.getTableId()))item.release(ReservationItem.Status.REMOVED,BigDecimal.ZERO,now,actor);
        var kept=new HashSet<Long>();for(var item:activeItems())kept.add(item.getTableId());
        boolean free=paymentStatus==PaymentStatus.NOT_REQUIRED;
        for(var t:tables)if(!kept.contains(t.getId()))items.add(new ReservationItem(this,t,free?BigDecimal.ZERO:priceOf.apply(t)));
        totalAmount=activeTotal();
    }
    /**
     * Cancels some tables of a multi-table booking and keeps the rest. The remaining tables must still seat the
     * party; otherwise the caller has to lower the guest count in this same operation.
     */
    public List<ReservationItem> cancelTables(Collection<Long> tableIds,Integer newGuestCount,Function<ReservationItem,BigDecimal> refundable,Instant now,String actor) {
        if(status!=Status.PENDING && status!=Status.CONFIRMED) throw new IllegalStateException("Only upcoming reservations can release tables");
        var ids=new LinkedHashSet<>(tableIds==null?List.<Long>of():tableIds);
        if(ids.isEmpty() || ids.size()!=tableIds.size()) throw new IllegalArgumentException("Choose each table to cancel once");
        var active=activeItems();
        var chosen=active.stream().filter(i->ids.contains(i.getTableId())).toList();
        if(chosen.size()!=ids.size()) throw new IllegalArgumentException("Only tables that are still part of this reservation can be cancelled");
        if(chosen.size()==active.size()) throw new IllegalStateException("At least one table must remain. Cancel the whole reservation to release every table.");
        int guests=newGuestCount==null?guestCount:newGuestCount;
        if(guests<1 || guests>guestCount) throw new IllegalArgumentException("The guest count can only be reduced when cancelling tables");
        int remaining=active.stream().filter(i->!ids.contains(i.getTableId())).mapToInt(ReservationItem::getCapacity).sum();
        if(remaining<guests) throw new IllegalStateException("The remaining tables seat "+remaining+". Reduce the guest count to "+remaining+" or fewer in the same request.");
        for(var item:chosen)item.release(ReservationItem.Status.CANCELLED,refundable.apply(item),now,actor);
        assignedTables.removeIf(t->ids.contains(t.getId()));
        table=assignedTables.iterator().next();guestCount=guests;
        return chosen;
    }
    public List<RestaurantTable> getAssignedTables() { return List.copyOf(assignedTables); }
    public List<ReservationItem> getItems() { return List.copyOf(items); }
    public List<ReservationItem> activeItems() { return items.stream().filter(ReservationItem::isActive).toList(); }
    /** Sum of the package prices of the tables still held. */
    public BigDecimal activeTotal() { return items.stream().filter(ReservationItem::isActive).map(ReservationItem::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add); }
    public String getGuestNeeds() { return guestNeeds; }
    public void setGuestNeeds(String needs) { if(needs==null || needs.length()>500) throw new IllegalArgumentException("Invalid guest needs");guestNeeds=needs; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(String value) { clientRequestId=value; }
    // ENCAPSULATION: Payment state moves only along UNPAID/REJECTED -> PENDING -> PAID/REJECTED.
    public void paymentSubmitted() {
        if(status!=Status.PENDING || !(paymentStatus==PaymentStatus.UNPAID || paymentStatus==PaymentStatus.REJECTED))
            throw new IllegalStateException("A payment cannot be submitted for this reservation in its current state");
        paymentStatus=PaymentStatus.PENDING;
    }
    public void paymentReviewed(boolean approved) {
        if(paymentStatus!=PaymentStatus.PENDING)throw new IllegalStateException("There is no submitted payment to review");
        paymentStatus=approved?PaymentStatus.PAID:PaymentStatus.REJECTED;
    }
    // ENCAPSULATION: Terminal states cannot be reopened through a generic status setter.
    public void confirm() {
        if(paymentStatus!=PaymentStatus.PAID && paymentStatus!=PaymentStatus.NOT_REQUIRED) throw new IllegalStateException("Verify the reservation payment before confirming");
        if (status != Status.PENDING) throw new IllegalStateException("Only pending reservations can be confirmed");
        status = Status.CONFIRMED;
    }
    public void cancel() { cancel(item->BigDecimal.ZERO,null,null); }
    /** Cancels the whole booking, recording what each table is refundable for under the cancellation policy. */
    public void cancel(Function<ReservationItem,BigDecimal> refundable,Instant now,String actor) {
        if (status != Status.PENDING && status != Status.CONFIRMED) throw new IllegalStateException("Reservation cannot be cancelled");
        for(var item:activeItems())item.release(ReservationItem.Status.CANCELLED,refundable.apply(item),now,actor);
        status = Status.CANCELLED;
    }
    public void complete(Clock clock) {
        if (status != Status.CONFIRMED || LocalDateTime.now(clock).isBefore(endsAt))
            throw new IllegalStateException("Only ended, confirmed reservations can be completed");
        status = Status.COMPLETED;
    }
    /** Half-open interval test: a booking that ends at 2:00 does not overlap one that starts at 2:00. */
    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        return (status == Status.PENDING || status == Status.CONFIRMED) && startsAt.isBefore(end) && endsAt.isAfter(start);
    }
    public Long getId() { return id; }
    public void setContactNumber(String phone) { contactNumber = PhoneNumbers.normalize(phone); }
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
