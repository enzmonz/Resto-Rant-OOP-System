package com.restorant.service;
import com.restorant.dto.Api;
import com.restorant.entity.Reservation;
import java.time.*;
import java.util.List;

// ABSTRACTION: HTTP controllers use a booking contract without depending on persistence or locking details.
public interface ReservationService {
    Api.BookingView create(String email, Api.Booking input);
    List<Api.BookingView> mine(String email);
    Api.BookingView detail(String email, Long id);
    Api.BookingView cancel(String email, Long id);
    /** Seating times for a party, using the given length of stay in minutes (null = restaurant default). */
    List<Api.Slot> availability(LocalDate date, int guests, Integer durationMinutes);
    default List<Api.Slot> availability(LocalDate date, int guests) { return availability(date, guests, null); }
    List<Integer> stayOptions();
    List<Api.BookingView> search(LocalDate date, Reservation.Status status, String query, Long customerId);
    Api.BookingView changeStatus(Long id, Reservation.Status status);
}
