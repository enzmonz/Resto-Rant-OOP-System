package com.restorant.service;
import com.restorant.dto.Api;
import com.restorant.entity.Reservation;
import java.time.*;
import java.util.List;

// ABSTRACTION: HTTP controllers use a booking contract without depending on persistence or locking details.
public interface ReservationService {
    // Write operations are deliberately not interface defaults: a default method would self-invoke and skip the
    // implementation's read-write transaction settings.
    /** Books for the customer with that email. actor is who performed the action (the customer, or a staff member). */
    Api.BookingView create(String email, Api.Booking input, String actor);
    List<Api.BookingView> mine(String email);
    Api.BookingView detail(String email, Long id);
    Api.BookingView cancel(String email, Long id);
    /** Releases some tables of a multi-table reservation and keeps the rest booked. */
    Api.BookingView cancelTables(String email, Long id, Api.CancelTables input);
    Api.BookingView cancelTablesAsStaff(Long id, Api.CancelTables input, String actor);
    /** Seating times for a party, using the given length of stay in minutes (null = restaurant default). */
    List<Api.Slot> availability(LocalDate date, int guests, Integer durationMinutes);
    default List<Api.Slot> availability(LocalDate date, int guests) { return availability(date, guests, null); }
    List<Integer> stayOptions();
    List<Api.BookingView> search(LocalDate date, Reservation.Status status, String query, Long customerId);
    Api.BookingView changeStatus(Long id, Reservation.Status status, String actor);
}
