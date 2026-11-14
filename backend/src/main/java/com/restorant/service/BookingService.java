package com.restorant.service;
import com.restorant.config.RestaurantSettings;
import com.restorant.config.DepositPolicy;
import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import com.restorant.repository.*;
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
    private final DepositPolicy deposit;
    public BookingService(ReservationRepository reservations, TableRepository tables, AccountService accounts,
                          BookingMutex mutex, RestaurantSettings settings, Clock clock,SeatingPlanner planner,DepositPolicy deposit) {
        this.reservations=reservations; this.tables=tables; this.accounts=accounts; this.mutex=mutex; this.settings=settings; this.clock=clock;
        this.planner=planner;this.deposit=deposit;
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
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView create(String email, Api.Booking input) {
        int minutes = stay(input.durationMinutes());
        var start = validate(input.date(), input.time(), input.guestCount(), minutes);
        var end = start.plusMinutes(minutes);
        var user = accounts.require(email);
        if (!(user instanceof Customer customer)) throw new ApiException(403, "Reservations require a customer account");
        mutex.acquire();
        // All booking and table mutations share this lock. READ_COMMITTED sees the previous holder's commit.
        if(!input.depositAccepted())throw new ApiException(400,"Please accept the deposit policy");
        if(deposit.getAmount().signum()<=0)throw new ApiException(503,"The restaurant has not configured its deposit policy yet. Please contact us.");
        var selected=allocate(input,start,end,null);
        var reservation = new Reservation(customer, selected, start, end, input.guestCount(), input.specialRequests(), clock);
        reservation.setContactNumber(input.contactNumber());
        reservation.setGuestNeeds(needs(input));reservation.requireDeposit(deposit.total(input.guestCount()));
        return view(reservations.saveAndFlush(reservation));
    }
    public Api.BookingView view(Reservation r) { return Api.BookingView.of(r, clock, settings.cancellationHours()); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView demoCheckout(String email,Api.DemoCheckout input) {
        mutex.acquire();
        String reference="GCASH-DEMO-"+input.checkoutId();
        var existing=reservations.findByPaymentReference(reference);
        if(existing.isPresent())return view(owned(email,existing.get().getId()));
        if(deposit.total(input.booking().guestCount()).compareTo(input.expectedAmount())!=0)
            throw new ApiException(409,"The deposit changed. Refresh the reservation page and review the new total.");
        var created=create(email,input.booking());
        return completeDemoPayment(require(created.id()),reference);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView demoPayment(String email,Long id) {
        mutex.acquire();var r=owned(email,id);
        if(r.getDepositStatus().equals("VERIFIED"))return view(r);
        return completeDemoPayment(r,"GCASH-DEMO-"+UUID.randomUUID());
    }
    private Api.BookingView completeDemoPayment(Reservation r,String reference) {
        r.submitPayment(reference);
        r.reviewPayment(true,"Automatically verified SIMULATED GCash payment. No real money transferred.");
        return view(r);
    }
    private String needs(Api.Booking input) {
        var allowed=Set.of("Wheelchair access","Baby/high chair","Parking space","Special occasion","Quiet area","Other assistance");
        var values=input.guestNeeds()==null?List.<String>of():input.guestNeeds();
        if(values.stream().anyMatch(n->!allowed.contains(n)))throw new ApiException(400,"Unknown guest assistance request");
        return String.join(", ",new LinkedHashSet<>(values));
    }
    private List<Reservation> occupied(LocalDateTime start,LocalDateTime end,Long excluded) {
        return reservations.activeDuring(start,end,ACTIVE).stream().filter(r->!Objects.equals(r.getId(),excluded)).toList();
    }
    private List<RestaurantTable> free(List<Reservation> busy) {
        var ids=new HashSet<Long>();for(var r:busy)for(var t:r.getAssignedTables())ids.add(t.getId());
        return tables.findAllByOrderByCapacityAscIdAsc().stream().filter(RestaurantTable::isEnabled).filter(t->!ids.contains(t.getId())).toList();
    }
    private List<RestaurantTable> allocate(Api.Booking input,LocalDateTime start,LocalDateTime end,Long excluded) {
        var busy=occupied(start,end,excluded);var available=free(busy);
        if(planner.remainingCapacity(busy,start,end)<input.guestCount())throw new ApiException(409,"The 100-person capacity is reached for this seating time");
        var requested=input.tableIds()==null?List.<Long>of():input.tableIds();
        List<RestaurantTable> selected;
        if(requested.isEmpty())selected=planner.bestFit(available,input.guestCount());
        else {
            if(new HashSet<>(requested).size()!=requested.size())throw new ApiException(400,"Select each table only once");
            selected=available.stream().filter(t->requested.contains(t.getId())).toList();
            if(selected.size()!=requested.size())throw new ApiException(409,"A selected table is already reserved or disabled. Please choose again.");
        }
        if(selected.isEmpty() || selected.stream().mapToInt(RestaurantTable::getCapacity).sum()<input.guestCount())throw new ApiException(409,"That seating time just filled up, or the selected tables have too few seats.");
        return selected;
    }
    public List<Api.TableView> seating(LocalDate date,LocalTime time,int guests,Integer duration) {
        int minutes=stay(duration);var start=validate(date,time,guests,minutes);var busy=occupied(start,start.plusMinutes(minutes),null);
        var available=free(busy).stream().map(RestaurantTable::getId).toList();
        return tables.findAllByOrderByCapacityAscIdAsc().stream().filter(RestaurantTable::isEnabled)
            .map(t->new Api.TableView(t.getId(),t.getTableNumber(),t.getCapacity(),true,available.contains(t.getId()))).toList();
    }
    public List<LocalDate> alternatives(LocalDate date,int guests) { return alternatives(date,guests,null); }
    public List<LocalDate> alternatives(LocalDate date,int guests,Integer duration) {
        var dates=new ArrayList<LocalDate>();var last=LocalDate.now(clock).plusDays(MAX_DAYS_AHEAD);
        for(int day=1;day<=30 && dates.size()<3 && !date.plusDays(day).isAfter(last);day++){var candidate=date.plusDays(day);if(availability(candidate,guests,duration).stream().anyMatch(s->s.availableTables()>0))dates.add(candidate);}
        return dates;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView submitPayment(String email,Long id,String reference) { mutex.acquire();var r=owned(email,id);r.submitPayment(reference);return view(r); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView reviewPayment(Long id,Api.PaymentReview review) { mutex.acquire();var r=require(id);r.reviewPayment(review.accepted(),review.note());return view(r); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView refund(Long id,String note) { mutex.acquire();var r=require(id);r.recordRefund(note);return view(r); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView edit(Long id,Api.Booking input) {
        mutex.acquire();var r=require(id);
        // Staff edits keep the booking's current length of stay unless a new one is chosen.
        int minutes=input.durationMinutes()==null?(int)Duration.between(r.getStartsAt(),r.getEndsAt()).toMinutes():stay(input.durationMinutes());
        var start=validate(input.date(),input.time(),input.guestCount(),minutes);var end=start.plusMinutes(minutes);
        if(input.guestCount()!=r.getGuestCount())throw new ApiException(409,"Guest count changes require a replacement reservation so the deposit stays correct");
        r.assign(allocate(input,start,end,id),input.guestCount(),start,end,input.specialRequests());r.setContactNumber(input.contactNumber());r.setGuestNeeds(needs(input));return view(r);
    }
    private Reservation require(Long id) { return reservations.findById(id).orElseThrow(() -> new ApiException(404, "Reservation not found")); }
    private Reservation owned(String email, Long id) {
        var r = require(id);
        if (!r.getCustomer().getId().equals(accounts.require(email).getId())) throw new ApiException(404, "Reservation not found");
        return r;
    }
    @Override public List<Api.BookingView> mine(String email) { return reservations.findByCustomerIdOrderByStartsAtDesc(accounts.require(email).getId()).stream().map(this::view).toList(); }
    @Override public Api.BookingView detail(String email, Long id) { return view(owned(email,id)); }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView cancel(String email, Long id) {
        mutex.acquire(); var r = owned(email,id);
        if (!view(r).cancellable()) throw new ApiException(409, "Cancellation is available until " + settings.cancellationHours() + " hours before your reservation");
        r.cancel(); return view(r);
    }
    // Declared here (not only as the interface default) so the class-level @Transactional applies.
    @Override public List<Api.Slot> availability(LocalDate date, int guests) { return availability(date, guests, null); }
    @Override public List<Api.Slot> availability(LocalDate date, int guests, Integer duration) {
        if (date == null || date.isBefore(LocalDate.now(clock)) || guests < 1 || guests > settings.maxGuests()) throw new ApiException(400, "Choose a current or future date and a valid party size");
        checkDate(date);
        int minutes = stay(duration);
        var result = new ArrayList<Api.Slot>();
        for (var start = date.atTime(settings.opening()); !start.plusMinutes(minutes).isAfter(date.atTime(settings.closing())); start = start.plusMinutes(settings.slotMinutes())) {
            if (!start.isAfter(LocalDateTime.now(clock))) continue;
            var end=start.plusMinutes(minutes);var busy=occupied(start,end,null);var available=free(busy);
            int seats=Math.min(planner.remainingCapacity(busy,start,end),available.stream().mapToInt(RestaurantTable::getCapacity).sum());
            result.add(new Api.Slot(start.toLocalTime(),seats>=guests?available.size():0,seats));
        }
        return result;
    }
    @Override public List<Api.BookingView> search(LocalDate date, Reservation.Status status, String query, Long customerId) {
        var q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return reservations.findAllByOrderByStartsAtDesc().stream()
            .filter(r -> date == null || r.getStartsAt().toLocalDate().equals(date))
            .filter(r -> status == null || r.getStatus() == status)
            .filter(r -> customerId == null || r.getCustomer().getId().equals(customerId))
            .filter(r -> (r.getReferenceNumber()+" "+r.getCustomer().getFullName()+" "+r.getCustomer().getEmail()).toLowerCase(Locale.ROOT).contains(q))
            .map(this::view).toList();
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView changeStatus(Long id, Reservation.Status status) {
        mutex.acquire(); var r = require(id);
        switch(status) {
            case CONFIRMED -> {
                if (!r.getStartsAt().isAfter(LocalDateTime.now(clock))) throw new ApiException(409, "Past reservations cannot be confirmed");
                r.confirm();
            }
            case CANCELLED -> r.cancel();
            case COMPLETED -> r.complete(clock);
            default -> throw new ApiException(400, "Reservations cannot be moved back to pending");
        }
        return view(r);
    }
}
