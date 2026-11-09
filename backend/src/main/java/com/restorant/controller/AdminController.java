package com.restorant.controller;
import com.restorant.config.RestaurantSettings;
import com.restorant.dto.Api;
import com.restorant.entity.Reservation;
import com.restorant.service.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final ReservationService bookings;
    private final RestaurantSettings settings;
    public AdminController(AdminService admin,ReservationService bookings,RestaurantSettings settings) { this.admin=admin; this.bookings=bookings; this.settings=settings; }
    @GetMapping("/overview") public Map<String,Long> overview() { return admin.overview(settings.durationMinutes()); }
    @GetMapping("/reservations") public List<Api.BookingView> search(@RequestParam(required=false) LocalDate date,
        @RequestParam(required=false) Reservation.Status status,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long customerId) {
        return bookings.search(date,status,q,customerId);
    }
    @PutMapping("/reservations/{id}/status") public Api.BookingView status(@PathVariable Long id,@Valid @RequestBody Api.StatusInput input) { return bookings.changeStatus(id,input.status()); }
    @GetMapping("/customers") public List<Api.Account> customers(@RequestParam(defaultValue="") String q) { return admin.customers(q); }
    @GetMapping("/customers/{id}/reservations") public List<Api.BookingView> history(@PathVariable Long id) { return bookings.search(null,null,"",id); }
    @GetMapping("/tables") public List<Api.TableView> tables(@RequestParam(required=false) LocalDateTime at) { return admin.tables(at,settings.durationMinutes()); }
    @PostMapping("/tables") @ResponseStatus(HttpStatus.CREATED) public Api.TableView create(@Valid @RequestBody Api.TableInput input) { return admin.save(null,input); }
    @PutMapping("/tables/{id}") public Api.TableView update(@PathVariable Long id,@Valid @RequestBody Api.TableInput input) { return admin.save(id,input); }
    @DeleteMapping("/tables/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void disable(@PathVariable Long id) { admin.disable(id); }
}
