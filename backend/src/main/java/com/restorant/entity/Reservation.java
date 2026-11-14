package com.restorant.entity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import java.util.*;
import java.math.BigDecimal;

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
    @ManyToMany
    @JoinTable(name="reservation_tables", joinColumns=@JoinColumn(name="reservation_id"), inverseJoinColumns=@JoinColumn(name="table_id"))
    private Set<RestaurantTable> assignedTables = new LinkedHashSet<>();
    @Column(nullable=false,length=500) private String guestNeeds = "";
    @Column(nullable=false,precision=12,scale=2) private BigDecimal depositAmount = BigDecimal.ZERO;
    @Column(nullable=false,length=25) private String depositStatus = "NOT_REQUIRED";
    @Column(length=150) private String paymentReference;
    @Column(length=500) private String paymentNote;
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
        assignedTables.add(table);
    }
    public Reservation(Customer customer, List<RestaurantTable> tables, LocalDateTime start, LocalDateTime end,
                       int guests, String requests, Clock clock) {
        this(customer, tables.getFirst(), start, end, Math.min(guests,tables.getFirst().getCapacity()), requests, clock);
        assign(tables,guests,start,end,requests);
    }
    // ENCAPSULATION: A booking owns a validated group of tables; callers cannot mutate that group directly.
    public void assign(List<RestaurantTable> tables,int guests,LocalDateTime start,LocalDateTime end,String requests) {
        if (tables.isEmpty() || new HashSet<>(tables).size()!=tables.size() || tables.stream().anyMatch(t -> !t.isEnabled())
            || guests<1 || tables.stream().mapToInt(RestaurantTable::getCapacity).sum()<guests)
            throw new IllegalArgumentException("Select enough distinct, enabled tables for your party");
        if(start==null || end==null || !end.isAfter(start) || (requests!=null && requests.length()>1000)) throw new IllegalArgumentException("Invalid reservation details");
        if(status==Status.CANCELLED || status==Status.COMPLETED) throw new IllegalStateException("Historical reservations cannot be edited");
        assignedTables.clear();assignedTables.addAll(tables);table=tables.getFirst();guestCount=guests;startsAt=start;endsAt=end;specialRequests=requests;
    }
    public List<RestaurantTable> getAssignedTables() { return List.copyOf(assignedTables); }
    public String getGuestNeeds() { return guestNeeds; }
    public void setGuestNeeds(String needs) { if(needs==null || needs.length()>500) throw new IllegalArgumentException("Invalid guest needs");guestNeeds=needs; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public String getDepositStatus() { return depositStatus; }
    public String getPaymentReference() { return paymentReference; }
    public String getPaymentNote() { return paymentNote; }
    public void requireDeposit(BigDecimal amount) {
        if(amount==null || amount.signum()<=0)throw new IllegalArgumentException("Deposit must be positive");
        depositAmount=amount;depositStatus="REQUIRED";
    }
    public void submitPayment(String reference) {
        if(status!=Status.PENDING || !(depositStatus.equals("REQUIRED") || depositStatus.equals("REJECTED"))) throw new IllegalStateException("Payment cannot be submitted in this state");
        if(reference==null || reference.isBlank() || reference.length()>150)throw new IllegalArgumentException("Enter a payment reference (up to 150 characters)");
        paymentReference=reference.trim();depositStatus="SUBMITTED";paymentNote=null;
    }
    public void reviewPayment(boolean accepted,String note) {
        if(!depositStatus.equals("SUBMITTED"))throw new IllegalStateException("No submitted deposit to review");
        if(note!=null && note.length()>500)throw new IllegalArgumentException("Payment note too long");
        depositStatus=accepted?(status==Status.CANCELLED?"REFUND_DUE":"VERIFIED"):"REJECTED";paymentNote=note;
    }
    public void recordRefund(String note) {
        if(!depositStatus.equals("REFUND_DUE"))throw new IllegalStateException("No verified deposit awaiting refund");
        if(note==null || note.isBlank() || note.length()>500)throw new IllegalArgumentException("Enter the refund reference");
        depositStatus="REFUNDED";paymentNote=note;
    }
    // ENCAPSULATION: Terminal states cannot be reopened through a generic status setter.
    public void confirm() {
        if(!depositStatus.equals("VERIFIED") && !depositStatus.equals("NOT_REQUIRED")) throw new IllegalStateException("Verify the reservation deposit before confirming");
        if (status != Status.PENDING) throw new IllegalStateException("Only pending reservations can be confirmed");
        status = Status.CONFIRMED;
    }
    public void cancel() {
        if (status != Status.PENDING && status != Status.CONFIRMED) throw new IllegalStateException("Reservation cannot be cancelled");
        status = Status.CANCELLED;
        if(depositStatus.equals("VERIFIED"))depositStatus="REFUND_DUE";
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
