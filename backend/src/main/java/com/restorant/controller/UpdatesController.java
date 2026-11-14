package com.restorant.controller;
import com.restorant.config.DepositPolicy;
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
    private final BookingService bookings;private final DepositPolicy deposit;private final InquiryService inquiries;
    private final AccountService accounts;private final AdminService admin;
    public UpdatesController(BookingService bookings,DepositPolicy deposit,InquiryService inquiries,AccountService accounts,AdminService admin){this.bookings=bookings;this.deposit=deposit;this.inquiries=inquiries;this.accounts=accounts;this.admin=admin;}
    @GetMapping("/deposit-policy") public DepositPolicy.View policy(){return deposit.snapshot();}
    @PostMapping("/reservations/demo-checkout") @ResponseStatus(HttpStatus.CREATED)
    public Api.BookingView demoCheckout(Principal user,@Valid @RequestBody Api.DemoCheckout input){return bookings.demoCheckout(user.getName(),input);}
    @PostMapping("/reservations/{id}/demo-payment")
    public Api.BookingView demoPayment(Principal user,@PathVariable Long id){return bookings.demoPayment(user.getName(),id);}
    @PutMapping("/admin/deposit-policy") public DepositPolicy.View policy(@Valid @RequestBody Api.DepositInput input){deposit.configure(input.amount(),input.method(),input.perPerson());return deposit.snapshot();}
    @GetMapping("/availability/tables") public List<Api.TableView> seating(@RequestParam LocalDate date,@RequestParam LocalTime time,@RequestParam int guests,@RequestParam(required=false) Integer duration){return bookings.seating(date,time,guests,duration);}
    @GetMapping("/availability/dates") public List<LocalDate> dates(@RequestParam LocalDate date,@RequestParam int guests,@RequestParam(required=false) Integer duration){return bookings.alternatives(date,guests,duration);}
    @PostMapping("/reservations/{id}/payment") public Api.BookingView payment(Principal user,@PathVariable Long id,@Valid @RequestBody Api.Payment input){return bookings.submitPayment(user.getName(),id,input.reference());}
    @PutMapping("/admin/reservations/{id}/payment") public Api.BookingView review(@PathVariable Long id,@Valid @RequestBody Api.PaymentReview input){return bookings.reviewPayment(id,input);}
    @PutMapping("/admin/reservations/{id}/refund") public Api.BookingView refund(@PathVariable Long id,@Valid @RequestBody Api.Payment input){return bookings.refund(id,input.reference());}
    @PutMapping("/admin/reservations/{id}") public Api.BookingView edit(@PathVariable Long id,@Valid @RequestBody Api.Booking input){return bookings.edit(id,input);}
    @PostMapping("/admin/customers/{id}/reservations") @ResponseStatus(HttpStatus.CREATED)
    public Api.BookingView create(@PathVariable Long id,@Valid @RequestBody Api.Booking input){return bookings.create(accounts.customer(id).getEmail(),input);}
    @DeleteMapping("/admin/reservations/{id}") public Api.BookingView cancel(@PathVariable Long id){return bookings.changeStatus(id,com.restorant.entity.Reservation.Status.CANCELLED);}
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
