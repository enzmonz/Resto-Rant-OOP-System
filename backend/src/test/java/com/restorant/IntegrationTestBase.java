package com.restorant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.repository.*;
import com.restorant.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Shared setup for tests that run the whole application against the in-memory database. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import(IntegrationTestBase.TestClockConfig.class)
abstract class IntegrationTestBase {
    static final String ALEX="alex@example.com", JAMIE="jamie@example.com", MANAGER="manager";
    static final ZoneId MANILA=ZoneId.of("Asia/Manila");

    /** The restaurant clock, with a switch to move "now" so cancellation cutoffs can be tested without waiting. */
    static class TestClock extends Clock {
        private volatile Duration shift=Duration.ZERO;
        void reset() { shift=Duration.ZERO; }
        /** Moves "now" in the restaurant timezone to the given wall-clock time. */
        void setNow(LocalDateTime manilaTime) { shift=Duration.between(Instant.now(),manilaTime.atZone(MANILA).toInstant()); }
        @Override public ZoneId getZone() { return MANILA; }
        @Override public Clock withZone(ZoneId zone) { return Clock.offset(Clock.system(zone),shift); }
        @Override public Instant instant() { return Instant.now().plus(shift); }
    }
    @TestConfiguration static class TestClockConfig {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TableRepository tables;
    @Autowired ReservationRepository reservations;
    @Autowired PaymentRepository payments;
    @Autowired PaymentProofRepository proofs;
    @Autowired RefundRepository refunds;
    @Autowired AuditRepository auditEntries;
    @Autowired TablePackageRepository packages;
    @Autowired ReservationService service;
    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired PricingService pricing;
    @Autowired PasswordEncoder encoder;
    @Autowired TestClock clock;
    final String password="Test-password-42";

    @BeforeEach void clean() {
        clock.reset(); bookingDay=LocalDate.now(clock).plusDays(2);
        auditEntries.deleteAll(); refunds.deleteAll(); proofs.deleteAll(); payments.deleteAll();
        reservations.deleteAll(); users.deleteAll(); tables.deleteAll(); packages.deleteAll();
        // The same sample prices the migration seeds: 2 seats 600, 4 seats 1,100, 6 seats 1,600.
        pricing.update(2,new BigDecimal("600"),"test");pricing.update(4,new BigDecimal("1100"),"test");pricing.update(6,new BigDecimal("1600"),"test");
        auditEntries.deleteAll();
        users.save(new Customer("Alex", ALEX, "09171234567", encoder.encode(password)));
        users.save(new Customer("Jamie", JAMIE, "09171234568", encoder.encode(password)));
        tables.save(new RestaurantTable("T01",4));
    }
    /** The day every test books: two days ahead in the restaurant timezone, fixed before a test moves the clock. */
    private LocalDate bookingDay;
    LocalDate day() { return bookingDay; }
    Api.Booking booking(LocalTime time) { return new Api.Booking(day(),time,4,"09171234567","Window seat"); }
    Api.Booking booking(LocalTime time,int guests,Integer minutes,List<Long> tableIds) {
        return new Api.Booking(day(),time,guests,"09171234567",null,tableIds,List.of(),true,minutes);
    }
    Api.BookingView book(String email,Api.Booking input) { return service.create(email,input,email); }
    /** The 25-table, 100-seat dining room: 8 tables for 2, 9 for 4 and 8 for 6. */
    void diningRoom() {
        tables.deleteAll();
        for(int i=1;i<=25;i++)tables.save(new RestaurantTable("D"+String.format("%02d",i),i<=8?2:i<=17?4:6));
    }
    /** Customer reports a GCash transfer; the payment stays pending. */
    Api.BookingView submitPayment(String email,Long reservationId,String reference) { return paymentService.submit(email,reservationId,reference,null); }
    /** Customer reports a transfer and staff verify it against the receiving account. */
    Api.BookingView pay(String email,Long reservationId,String reference) {
        var pending=submitPayment(email,reservationId,reference);
        paymentService.review(pending.payments().getLast().id(),new Api.PaymentReview(true,true,"Seen in GCash transaction history"),MANAGER);
        return service.detail(email,reservationId);
    }
    Api.Slot slot(List<Api.Slot> slots,LocalTime time) { return slots.stream().filter(s->s.time().equals(time)).findFirst().orElseThrow(); }
}
