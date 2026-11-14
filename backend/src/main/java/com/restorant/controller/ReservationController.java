package com.restorant.controller;
import com.restorant.config.RestaurantSettings;
import com.restorant.dto.Api;
import com.restorant.service.ReservationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class ReservationController {
    private final ReservationService service;
    private final RestaurantSettings settings;
    private final Clock clock;
    public ReservationController(ReservationService service,RestaurantSettings settings,Clock clock) { this.service=service; this.settings=settings; this.clock=clock; }
    @GetMapping("/info") public Map<String,Object> info() {
        var today=LocalDate.now(clock);
        return Map.of("settings",settings,"today",today,"now",LocalDateTime.now(clock),
            "stayOptions",service.stayOptions(),"lastBookableDate",today.plusDays(com.restorant.service.BookingService.MAX_DAYS_AHEAD));
    }
    @GetMapping("/availability") public List<Api.Slot> availability(@RequestParam LocalDate date,@RequestParam int guests,@RequestParam(required=false) Integer duration) { return service.availability(date,guests,duration); }
    @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED)
    public Api.BookingView create(Principal user,@Valid @RequestBody Api.Booking input) { return service.create(user.getName(),input); }
    @GetMapping("/reservations/my") public List<Api.BookingView> mine(Principal user) { return service.mine(user.getName()); }
    @GetMapping("/reservations/{id}") public Api.BookingView detail(Principal user,@PathVariable Long id) { return service.detail(user.getName(),id); }
    @PutMapping("/reservations/{id}/cancel") public Api.BookingView cancel(Principal user,@PathVariable Long id) { return service.cancel(user.getName(),id); }
}
