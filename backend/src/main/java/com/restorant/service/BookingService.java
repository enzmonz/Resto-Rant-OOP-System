package com.restorant.service;
import com.restorant.config.RestaurantSettings;
import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import com.restorant.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(readOnly = true)
public class BookingService implements ReservationService {
    public static final List<Reservation.Status> ACTIVE = List.of(Reservation.Status.PENDING, Reservation.Status.CONFIRMED);
    private final ReservationRepository reservations;
    private final TableRepository tables;
    private final AccountService accounts;
    private final BookingMutex mutex;
    private final RestaurantSettings settings;
    private final Clock clock;
    private final SeatingPlanner planner;
    private final PricingService pricing;
    private final CancellationPolicy policy;
    private final ReservationViews views;
    private final AuditLog audit;
    public BookingService(ReservationRepository reservations, TableRepository tables, AccountService accounts,
                          BookingMutex mutex, RestaurantSettings settings, Clock clock, SeatingPlanner planner,
                          PricingService pricing, CancellationPolicy policy, ReservationViews views, AuditLog audit) {
        this.reservations=reservations; this.tables=tables; this.accounts=accounts; this.mutex=mutex; this.settings=settings; this.clock=clock;
        this.planner=planner; this.pricing=pricing; this.policy=policy; this.views=views; this.audit=audit;
    }
    /** Lengths of stay a guest may choose, in minutes. */
    public static final List<Integer> STAY_OPTIONS = List.of(60, 90, 120, 150, 180);
    /** How far ahead a reservation may be made. */
    public static final int MAX_DAYS_AHEAD = 365;
    /** Returns the requested length of stay, or the restaurant default when none was chosen. */
    public int stay(Integer requested) {
        if (requested == null) return settings.durationMinutes();
        if (!STAY_OPTIONS.contains(requested)) throw new ApiException(400, "Choose a length of stay between 1 and 3 hours");
        return requested;
    }
    public List<Integer> stayOptions() { return STAY_OPTIONS; }
    private void checkDate(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now(clock).plusDays(MAX_DAYS_AHEAD)))
            throw new ApiException(400, "Reservations can be made up to " + MAX_DAYS_AHEAD + " days ahead");
    }
    /** A start time is valid only if it is in the future, on the slot grid, and the whole stay ends by closing time. */
    private LocalDateTime validate(LocalDate date, LocalTime time, int guests, int minutes) {
        if (date == null || time == null || guests < 1 || guests > settings.maxGuests()) throw new ApiException(400, "Choose a date, time and 1–" + settings.maxGuests() + " guests");
        checkDate(date);
        var start = LocalDateTime.of(date, time);
        long offset = Duration.between(settings.opening(), time).toMinutes();
        if (!start.isAfter(LocalDateTime.now(clock))) throw new ApiException(400, "Choose a future reservation time");
        if (time.isBefore(settings.opening()) || start.plusMinutes(minutes).isAfter(date.atTime(settings.closing()))
            || offset % settings.slotMinutes() != 0 || time.getSecond() != 0 || time.getNano() != 0)
            throw new ApiException(400, "Choose one of the available seating times within opening hours");
        return start;
    }

    // ---- Availability: one rule shared by the time list, the table map, automatic assignment and booking ----

    /** What is free for one stay [start, end): the reservations in the way, the tables nobody holds, and the seats a party can use. */
    private record Seating(List<Reservation> busy, List<RestaurantTable> free, int seatsLeft) {}
    private List<RestaurantTable> enabledTables() { return tables.findAllByOrderByCapacityAscIdAsc().stream().filter(RestaurantTable::isEnabled).toList(); }
    /**
     * A table is free for a stay only if no active reservation holding it overlaps any part of [start, end).
     * Intervals are half-open, so a table released at 2:00 can be booked from 2:00.
     */
    private Seating seatingFor(List<RestaurantTable> enabled, List<Reservation> candidates, LocalDateTime start, LocalDateTime end, Long excluded) {
        var busy=candidates.stream().filter(r->!Objects.equals(r.getId(),excluded) && r.overlaps(start,end)).toList();
        var taken=new HashSet<Long>();for(var r:busy)for(var t:r.getAssignedTables())taken.add(t.getId());
        var free=enabled.stream().filter(t->!taken.contains(t.getId())).toList();
        int seats=Math.min(planner.remainingCapacity(busy,start,end),free.stream().mapToInt(RestaurantTable::getCapacity).sum());
        return new Seating(busy,free,seats);
    }
    private Seating seatingAt(LocalDateTime start, LocalDateTime end, Long excluded) {
        return seatingFor(enabledTables(),reservations.activeDuring(start,end,ACTIVE),start,end,excluded);
    }
    /**
     * Chooses the tables for a booking. Whether picked by the guest or automatically, the result is a set of
     * distinct, free tables whose own seats cover the party: 100 guests need tables adding up to 100 seats.
     */
    private List<RestaurantTable> allocate(Api.Booking input,LocalDateTime start,LocalDateTime end,Long excluded) {
        var seating=seatingAt(start,end,excluded);int guests=input.guestCount();
        if(planner.remainingCapacity(seating.busy(),start,end)<guests)throw new ApiException(409,"The 100-person capacity is reached for this seating time");
        var requested=input.tableIds()==null?List.<Long>of():input.tableIds();
        if(requested.isEmpty()) {
            var chosen=planner.bestFit(seating.free(),guests);
            if(chosen.isEmpty())throw new ApiException(409,"That seating time just filled up: the free tables no longer seat "+guests+" guests for the whole stay.");
            return chosen;
        }
        if(new HashSet<>(requested).size()!=requested.size())throw new ApiException(400,"Select each table only once");
        var selected=seating.free().stream().filter(t->requested.contains(t.getId())).toList();
        if(selected.size()!=requested.size())throw new ApiException(409,"A selected table is already reserved or disabled for part of that stay. Please choose again.");
        int seats=selected.stream().mapToInt(RestaurantTable::getCapacity).sum();
        if(seats<guests)throw new ApiException(409,"The selected tables have too few seats: they seat "+seats+" of your "+guests+" guests. Add more tables.");
        return selected;
    }
    private String needs(Api.Booking input) {
        var allowed=Set.of("Wheelchair access","Baby/high chair","Parking space","Special occasion","Quiet area","Other assistance");
        var values=input.guestNeeds()==null?List.<String>of():input.guestNeeds();
        if(values.stream().anyMatch(n->!allowed.contains(n)))throw new ApiException(400,"Unknown guest assistance request");
        return String.join(", ",new LinkedHashSet<>(values));
    }

    // ---- Booking ----

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView create(String email, Api.Booking input, String actor) {
        int minutes = stay(input.durationMinutes());
        var user = accounts.require(email);
        if (!(user instanceof Customer customer)) throw new ApiException(403, "Reservations require a customer account");
        // All booking, table, price and payment mutations share this lock. READ_COMMITTED sees the previous holder's commit,
        // so the availability and price checks below are repeated atomically for every request.
        mutex.acquire();
        if(input.requestId()!=null) {
            var earlier=reservations.findByClientRequestId(input.requestId().toString());
            if(earlier.isPresent()) {
                if(!earlier.get().getCustomer().getId().equals(customer.getId()))throw new ApiException(409,"This request was already used. Refresh the page and try again.");
                return views.of(earlier.get());
            }
        }
        var start = validate(input.date(), input.time(), input.guestCount(), minutes);
        var end = start.plusMinutes(minutes);
        if(!input.policyAccepted())throw new ApiException(400,"Please accept the package price and cancellation policy");
        var selected=allocate(input,start,end,null);
        var reservation = new Reservation(customer, selected, start, end, input.guestCount(), input.specialRequests(), clock);
        reservation.setContactNumber(input.contactNumber());
        reservation.setGuestNeeds(needs(input));
        if(input.requestId()!=null)reservation.setClientRequestId(input.requestId().toString());
        var prices=pricing.priceList();
        reservation.priceItems(t->prices.priceFor(t.getCapacity()));
        // The client never supplies a price. It may only state the total it displayed, so a price change is surfaced instead of surprising the guest.
        if(input.expectedTotal()!=null && input.expectedTotal().compareTo(reservation.getTotalAmount())!=0)
            throw new ApiException(409,"The package prices or the assigned tables changed. Review the updated total before reserving.");
        var saved=reservations.saveAndFlush(reservation);
        audit.record(saved.getId(),null,null,"RESERVATION_CREATED",describe(saved.getItems())+" · total "+saved.getTotalAmount().toPlainString()+" · "+saved.getGuestCount()+" guests",actor);
        return views.of(saved);
    }
    private static String describe(List<ReservationItem> items) {
        return String.join(", ",items.stream().map(i->"Table "+i.getTableNumber()+" ("+i.getCapacity()+" seats) "+i.getPrice().toPlainString()).toList());
    }
    public Api.BookingView view(Reservation r) { return views.of(r); }

    public List<Api.TableView> seating(LocalDate date,LocalTime time,int guests,Integer duration) {
        int minutes=stay(duration);var start=validate(date,time,guests,minutes);var seating=seatingAt(start,start.plusMinutes(minutes),null);
        var free=new HashSet<Long>();for(var t:seating.free())free.add(t.getId());
        // The automatic assignment is computed here so the tables highlighted in the browser are the ones the server would choose.
        var suggested=new HashSet<Long>();
        if(seating.seatsLeft()>=guests)for(var t:planner.bestFit(seating.free(),guests))suggested.add(t.getId());
        var prices=pricing.priceList();
        return enabledTables().stream().map(t->new Api.TableView(t.getId(),t.getTableNumber(),t.getCapacity(),true,free.contains(t.getId()),
            prices.priceFor(t.getCapacity()),suggested.contains(t.getId()))).toList();
    }
    public List<LocalDate> alternatives(LocalDate date,int guests) { return alternatives(date,guests,null); }
    public List<LocalDate> alternatives(LocalDate date,int guests,Integer duration) {
        var dates=new ArrayList<LocalDate>();var last=LocalDate.now(clock).plusDays(MAX_DAYS_AHEAD);
        for(int day=1;day<=30 && dates.size()<3 && !date.plusDays(day).isAfter(last);day++){var candidate=date.plusDays(day);if(availability(candidate,guests,duration).stream().anyMatch(s->s.availableTables()>0))dates.add(candidate);}
        return dates;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView edit(Long id,Api.Booking input,String actor) {
        mutex.acquire();var r=require(id);
        // Staff edits keep the booking's current length of stay unless a new one is chosen.
        int minutes=input.durationMinutes()==null?(int)Duration.between(r.getStartsAt(),r.getEndsAt()).toMinutes():stay(input.durationMinutes());
        var start=validate(input.date(),input.time(),input.guestCount(),minutes);var end=start.plusMinutes(minutes);
        if(input.guestCount()!=r.getGuestCount())throw new ApiException(409,"Guest count changes are made by cancelling tables or with a replacement reservation, so the package price stays correct");
        var prices=pricing.priceList();
        BigDecimal before=r.activeTotal();
        r.reassign(allocate(input,start,end,id),input.guestCount(),start,end,input.specialRequests(),t->prices.priceFor(t.getCapacity()),clock.instant(),actor);
        r.setContactNumber(input.contactNumber());r.setGuestNeeds(needs(input));
        reservations.saveAndFlush(r);
        audit.record(r.getId(),null,null,"RESERVATION_EDITED",describe(r.activeItems())+" · total "+before.toPlainString()+" -> "+r.activeTotal().toPlainString(),actor);
        return views.of(r);
    }
    private Reservation require(Long id) { return reservations.findById(id).orElseThrow(() -> new ApiException(404, "Reservation not found")); }
    private Reservation owned(String email, Long id) {
        var r = require(id);
        if (!r.getCustomer().getId().equals(accounts.require(email).getId())) throw new ApiException(404, "Reservation not found");
        return r;
    }
    @Override public List<Api.BookingView> mine(String email) { return views.of(reservations.findByCustomerIdOrderByStartsAtDesc(accounts.require(email).getId())); }
    @Override public Api.BookingView detail(String email, Long id) { return views.of(owned(email,id)); }

    // ---- Cancellation: the same policy for a whole reservation and for single tables ----

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView cancel(String email, Long id) {
        mutex.acquire(); var r = owned(email,id);
        if (!policy.customerMayCancel(r)) throw new ApiException(409, "Cancellation is available until " + settings.cancellationHours() + " hours before your reservation");
        cancelAll(r,email); return views.of(r);
    }
    private void cancelAll(Reservation r,String actor) {
        var released=r.activeItems();
        r.cancel(item->policy.refundable(item.getPrice(),r.getStartsAt()),clock.instant(),actor);
        reservations.saveAndFlush(r);
        audit.record(r.getId(),null,null,"RESERVATION_CANCELLED",cancellationDetail(released),actor);
    }
    private static String cancellationDetail(List<ReservationItem> released) {
        BigDecimal refundable=released.stream().map(ReservationItem::getRefundableAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return "Released "+describe(released)+" · refundable under policy "+refundable.toPlainString();
    }
    /** Customer cancels some tables of their own reservation, before the cancellation cutoff. */
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView cancelTables(String email, Long id, Api.CancelTables input) {
        mutex.acquire(); var r = owned(email,id);
        if (!policy.customerMayCancel(r)) throw new ApiException(409, "Tables can be cancelled until " + settings.cancellationHours() + " hours before your reservation");
        return releaseTables(r,input,email);
    }
    /** Staff cancel some tables at any time before the visit ends; late cancellations are recorded as non-refundable. */
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView cancelTablesAsStaff(Long id, Api.CancelTables input, String actor) {
        mutex.acquire(); return releaseTables(require(id),input,actor);
    }
    private Api.BookingView releaseTables(Reservation r,Api.CancelTables input,String actor) {
        int before=r.getGuestCount();
        var released=r.cancelTables(input.tableIds(),input.guestCount(),item->policy.refundable(item.getPrice(),r.getStartsAt()),clock.instant(),actor);
        reservations.saveAndFlush(r);
        audit.record(r.getId(),null,null,"TABLES_CANCELLED",cancellationDetail(released)+" · guests "+before+" -> "+r.getGuestCount()
            +" · still booked: "+describe(r.activeItems()),actor);
        return views.of(r);
    }

    // Declared here (not only as the interface default) so the class-level @Transactional applies.
    @Override public List<Api.Slot> availability(LocalDate date, int guests) { return availability(date, guests, null); }
    /**
     * Start times for one day and one length of stay. A time is listed only when the whole stay ends by closing,
     * and it is marked available only when free tables can seat the party for every minute of that stay.
     */
    @Override public List<Api.Slot> availability(LocalDate date, int guests, Integer duration) {
        if (date == null || date.isBefore(LocalDate.now(clock)) || guests < 1 || guests > settings.maxGuests()) throw new ApiException(400, "Choose a current or future date and a valid party size");
        checkDate(date);
        int minutes = stay(duration);
        var open=date.atTime(settings.opening());var close=date.atTime(settings.closing());var now=LocalDateTime.now(clock);
        var enabled=enabledTables();var day=reservations.activeDuring(open,close,ACTIVE);
        var result = new ArrayList<Api.Slot>();
        for (var start = open; !start.plusMinutes(minutes).isAfter(close); start = start.plusMinutes(settings.slotMinutes())) {
            if (!start.isAfter(now)) continue;
            var end=start.plusMinutes(minutes);var seating=seatingFor(enabled,day,start,end,null);
            result.add(new Api.Slot(start.toLocalTime(),end.toLocalTime(),seating.seatsLeft()>=guests?seating.free().size():0,seating.seatsLeft()));
        }
        return result;
    }
    @Override public List<Api.BookingView> search(LocalDate date, Reservation.Status status, String query, Long customerId) {
        var q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return views.of(reservations.findAllByOrderByStartsAtDesc().stream()
            .filter(r -> date == null || r.getStartsAt().toLocalDate().equals(date))
            .filter(r -> status == null || r.getStatus() == status)
            .filter(r -> customerId == null || r.getCustomer().getId().equals(customerId))
            .filter(r -> (r.getReferenceNumber()+" "+r.getCustomer().getFullName()+" "+r.getCustomer().getEmail()).toLowerCase(Locale.ROOT).contains(q))
            .toList());
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView changeStatus(Long id, Reservation.Status status, String actor) {
        mutex.acquire(); var r = require(id);
        switch(status) {
            case CONFIRMED -> {
                if (!r.getStartsAt().isAfter(LocalDateTime.now(clock))) throw new ApiException(409, "Past reservations cannot be confirmed");
                r.confirm();
            }
            case CANCELLED -> cancelAll(r,actor);
            case COMPLETED -> r.complete(clock);
            default -> throw new ApiException(400, "Reservations cannot be moved back to pending");
        }
        return views.of(r);
    }
}
