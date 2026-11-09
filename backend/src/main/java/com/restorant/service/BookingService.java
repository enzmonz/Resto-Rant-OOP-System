package com.restorant.service;
import com.restorant.config.RestaurantSettings;
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
    public BookingService(ReservationRepository reservations, TableRepository tables, AccountService accounts,
                          BookingMutex mutex, RestaurantSettings settings, Clock clock) {
        this.reservations=reservations; this.tables=tables; this.accounts=accounts; this.mutex=mutex; this.settings=settings; this.clock=clock;
    }
    private LocalDateTime validate(LocalDate date, LocalTime time, int guests) {
        if (date == null || time == null || guests < 1 || guests > settings.maxGuests()) throw new ApiException(400, "Choose a date, time and 1–" + settings.maxGuests() + " guests");
        var start = LocalDateTime.of(date, time);
        long offset = Duration.between(settings.opening(), time).toMinutes();
        if (!start.isAfter(LocalDateTime.now(clock))) throw new ApiException(400, "Choose a future reservation time");
        if (time.isBefore(settings.opening()) || start.plusMinutes(settings.durationMinutes()).isAfter(date.atTime(settings.closing()))
            || offset % settings.slotMinutes() != 0 || time.getSecond() != 0 || time.getNano() != 0)
            throw new ApiException(400, "Choose one of the available seating times within opening hours");
        return start;
    }
    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Api.BookingView create(String email, Api.Booking input) {
        var start = validate(input.date(), input.time(), input.guestCount());
        var end = start.plusMinutes(settings.durationMinutes());
        var user = accounts.require(email);
        if (!(user instanceof Customer customer)) throw new ApiException(403, "Reservations require a customer account");
        mutex.acquire();
        // All booking and table mutations share this lock. READ_COMMITTED sees the previous holder's commit.
        var table = tables.findAllByOrderByCapacityAscIdAsc().stream().filter(t -> t.accommodates(input.guestCount()))
            .filter(t -> reservations.overlaps(t.getId(), start, end, ACTIVE) == 0).findFirst()
            .orElseThrow(() -> new ApiException(409, "That seating time just filled up. Please choose another time."));
        var reservation = new Reservation(customer, table, start, end, input.guestCount(), input.specialRequests(), clock);
        reservation.setContactNumber(input.contactNumber());
        return view(reservations.saveAndFlush(reservation));
    }
    public Api.BookingView view(Reservation r) { return Api.BookingView.of(r, clock, settings.cancellationHours()); }
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
    @Override public List<Api.Slot> availability(LocalDate date, int guests) {
        if (date == null || date.isBefore(LocalDate.now(clock)) || guests < 1 || guests > settings.maxGuests()) throw new ApiException(400, "Choose a current or future date and a valid party size");
        var result = new ArrayList<Api.Slot>();
        var candidates = tables.findAllByOrderByCapacityAscIdAsc().stream().filter(t -> t.accommodates(guests)).toList();
        for (var start = date.atTime(settings.opening()); !start.plusMinutes(settings.durationMinutes()).isAfter(date.atTime(settings.closing())); start = start.plusMinutes(settings.slotMinutes())) {
            if (!start.isAfter(LocalDateTime.now(clock))) continue;
            int count = 0;
            for (var t : candidates) if (reservations.overlaps(t.getId(), start, start.plusMinutes(settings.durationMinutes()), ACTIVE) == 0) count++;
            result.add(new Api.Slot(start.toLocalTime(), count));
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
