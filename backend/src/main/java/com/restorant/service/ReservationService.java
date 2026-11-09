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
    List<Api.Slot> availability(LocalDate date, int guests);
    List<Api.BookingView> search(LocalDate date, Reservation.Status status, String query, Long customerId);
    Api.BookingView changeStatus(Long id, Reservation.Status status);
}
