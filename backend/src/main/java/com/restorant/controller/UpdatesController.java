package com.restorant.controller;
import com.restorant.dto.Api;
import com.restorant.service.*;
import com.restorant.entity.Inquiry;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api")
public class UpdatesController {
    private final BookingService bookings;private final InquiryService inquiries;
    private final AccountService accounts;private final AdminService admin;
    public UpdatesController(BookingService bookings,InquiryService inquiries,AccountService accounts,AdminService admin){this.bookings=bookings;this.inquiries=inquiries;this.accounts=accounts;this.admin=admin;}
    @GetMapping("/availability/tables") public List<Api.TableView> seating(@RequestParam LocalDate date,@RequestParam LocalTime time,@RequestParam int guests,@RequestParam(required=false) Integer duration){return bookings.seating(date,time,guests,duration);}
    @GetMapping("/availability/dates") public List<LocalDate> dates(@RequestParam LocalDate date,@RequestParam int guests,@RequestParam(required=false) Integer duration){return bookings.alternatives(date,guests,duration);}
    @PutMapping("/admin/reservations/{id}") public Api.BookingView edit(Principal staff,@PathVariable Long id,@Valid @RequestBody Api.Booking input){return bookings.edit(id,input,staff.getName());}
    @PostMapping("/admin/customers/{id}/reservations") @ResponseStatus(HttpStatus.CREATED)
    public Api.BookingView create(Principal staff,@PathVariable Long id,@Valid @RequestBody Api.Booking input){return bookings.create(accounts.customer(id).getEmail(),input,staff.getName());}
    @DeleteMapping("/admin/reservations/{id}") public Api.BookingView cancel(Principal staff,@PathVariable Long id){return bookings.changeStatus(id,com.restorant.entity.Reservation.Status.CANCELLED,staff.getName());}
    @PostMapping("/admin/reservations/{id}/cancel-tables")
    public Api.BookingView cancelTables(Principal staff,@PathVariable Long id,@Valid @RequestBody Api.CancelTables input){return bookings.cancelTablesAsStaff(id,input,staff.getName());}
    @PostMapping("/admin/accounts") @ResponseStatus(HttpStatus.CREATED) public Api.Account admin(@Valid @RequestBody Api.Register input){return accounts.registerAdmin(input);}
    @GetMapping("/admin/accounts") public List<Api.Account> staff(){return accounts.staff();}
    @PostMapping("/admin/customers") @ResponseStatus(HttpStatus.CREATED) public Api.Account customer(@Valid @RequestBody Api.Register input){return accounts.register(input);}
    @PutMapping("/admin/customers/{id}") public Api.Account customer(@PathVariable Long id,@Valid @RequestBody Api.Profile input){return accounts.updateCustomer(id,input);}
    @DeleteMapping("/admin/customers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void disable(@PathVariable Long id){accounts.disableCustomer(id);}
    @PostMapping("/admin/layout") public Map<String,String> layout(){admin.applyLayout();return Map.of("message","25-table balanced layout applied");}
    @PostMapping("/inquiries") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> inquiry(@Valid @RequestBody Api.InquiryInput input){return Map.of("id",inquiries.submit(input),"message","Your inquiry is saved for our restaurant team.");}
    @GetMapping("/admin/inquiries") public List<Inquiry> inquiries(){return inquiries.list();}
    @PutMapping("/admin/inquiries/{id}/resolve") @ResponseStatus(HttpStatus.NO_CONTENT) public void resolve(@PathVariable Long id){inquiries.resolve(id);}
}
