package com.restorant.controller;
import com.restorant.config.RestaurantSettings;
import com.restorant.dto.Api;
import com.restorant.service.ReservationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class ReservationController {
    private final ReservationService service;
    private final RestaurantSettings settings;
    private final Clock clock;
    public ReservationController(ReservationService service,RestaurantSettings settings,Clock clock) { this.service=service; this.settings=settings; this.clock=clock; }
    /**
     * The browser takes "today", "now" and the booking window from here. They are computed from the server's clock in
     * the restaurant's timezone, so the visitor's own clock and timezone never decide which dates or times are offered.
     */
    @GetMapping("/info") public Map<String,Object> info() {
        var now=ZonedDateTime.now(clock);var today=now.toLocalDate();
        String offset=now.getOffset().getId().equals("Z")?"+00:00":now.getOffset().getId();
        String name=settings.zone().getDisplayName(TextStyle.FULL,Locale.ENGLISH);
        var result=new LinkedHashMap<String,Object>();
        result.put("settings",settings);result.put("today",today);result.put("now",now.toLocalDateTime().withNano(0));
        result.put("zone",settings.zone().getId());result.put("utcOffset",offset);
        result.put("zoneLabel",name+" ("+settings.zone().getId()+", UTC"+offset+")");
        result.put("stayOptions",service.stayOptions());result.put("lastBookableDate",today.plusDays(com.restorant.service.BookingService.MAX_DAYS_AHEAD));
        return result;
    }
    @GetMapping("/availability") public List<Api.Slot> availability(@RequestParam LocalDate date,@RequestParam int guests,@RequestParam(required=false) Integer duration) { return service.availability(date,guests,duration); }
    @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED)
    public Api.BookingView create(Principal user,@Valid @RequestBody Api.Booking input) { return service.create(user.getName(),input,user.getName()); }
    @GetMapping("/reservations/my") public List<Api.BookingView> mine(Principal user) { return service.mine(user.getName()); }
    @GetMapping("/reservations/{id}") public Api.BookingView detail(Principal user,@PathVariable Long id) { return service.detail(user.getName(),id); }
    @PutMapping("/reservations/{id}/cancel") public Api.BookingView cancel(Principal user,@PathVariable Long id) { return service.cancel(user.getName(),id); }
    @PostMapping("/reservations/{id}/cancel-tables")
    public Api.BookingView cancelTables(Principal user,@PathVariable Long id,@Valid @RequestBody Api.CancelTables input) { return service.cancelTables(user.getName(),id,input); }
}
