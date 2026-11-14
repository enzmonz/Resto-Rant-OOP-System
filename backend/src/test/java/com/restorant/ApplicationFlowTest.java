package com.restorant;

import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.repository.*;
import com.restorant.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ApplicationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TableRepository tables;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationService service;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired BookingService bookingService;
    @Autowired com.restorant.config.DepositPolicy depositPolicy;
    private final String password="Test-password-42";
    @BeforeEach void clean() {
        reservations.deleteAll(); users.deleteAll(); tables.deleteAll();
        depositPolicy.configure(new java.math.BigDecimal("500"),"GCash demo — no real payment",true);
        users.save(new Customer("Alex", "alex@example.com", "09171234567", encoder.encode(password)));
        users.save(new Customer("Jamie", "jamie@example.com", "09171234568", encoder.encode(password)));
        tables.save(new RestaurantTable("T01",4));
    }
    private Api.Booking booking(LocalTime time) { return new Api.Booking(LocalDate.now(clock).plusDays(2),time,4,"09171234567","Window seat"); }
    @Test void registrationHashesPasswordRejectsDuplicateAndValidatesInput() throws Exception {
        var registration=new Api.Register("Sam","SAM@example.com","09171234569",password,password);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(registration))).andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(encoder.matches(password,users.findByEmail("sam@example.com").orElseThrow().getPasswordHash())).isTrue();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(registration))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"email\":\"bad\"}")).andExpect(status().isBadRequest());
    }
    @Test void realLoginPersistsSessionAndLogoutClearsIt() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(new Api.Login("alex@example.com","wrong-password")))).andExpect(status().isUnauthorized());
        var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(new Api.Login("alex@example.com",password)))).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alex@example.com"));
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void authorizationAndCsrfProtectEndpoints() throws Exception {
        mvc.perform(get("/api/reservations/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/overview").with(user("alex@example.com").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/overview").with(user("manager").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/reservations").with(user("alex@example.com").roles("CUSTOMER")).contentType("application/json").content(json.writeValueAsString(booking(LocalTime.NOON)))).andExpect(status().isForbidden());
    }
    @Test void customerCanOnlySeeAndCancelOwnReservation() throws Exception {
        var r=service.create("alex@example.com",booking(LocalTime.NOON));
        mvc.perform(get("/api/reservations/"+r.id()).with(user("jamie@example.com").roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/reservations/"+r.id()+"/cancel").with(csrf()).with(user("jamie@example.com").roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/reservations/"+r.id()+"/cancel").with(csrf()).with(user("alex@example.com").roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(service.create("jamie@example.com",booking(LocalTime.NOON)).status()).isEqualTo(Reservation.Status.PENDING);
    }
    @Test void overlapIsRejectedButAdjacentSeatingIsAllowed() {
        service.create("alex@example.com",booking(LocalTime.NOON));
        assertThatThrownBy(()->service.create("jamie@example.com",booking(LocalTime.of(13,30)))).hasMessageContaining("filled up");
        assertThat(service.create("jamie@example.com",booking(LocalTime.of(14,0))).id()).isNotNull();
        assertThat(service.availability(LocalDate.now(clock).plusDays(2),4).stream().filter(s->s.time().equals(LocalTime.NOON)).findFirst().orElseThrow().availableTables()).isZero();
    }
    @Test void concurrentRequestsCannotDoubleBookLastTable() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Boolean>>();
            for(var email:List.of("alex@example.com","jamie@example.com")) tasks.add(pool.submit(()->{start.await();try{service.create(email,booking(LocalTime.NOON));return true;}catch(com.restorant.exception.ApiException e){if(e.getStatus()!=409)throw e;return false;}}));
            start.countDown();
            int successes=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);assertThat(reservations.count()).isEqualTo(1);
        }
    }
    @Test void invalidTimeAndGuestCountAreRejected() throws Exception {
        assertThatThrownBy(()->service.create("alex@example.com",booking(LocalTime.of(23,0)))).hasMessageContaining("opening hours");
        mvc.perform(post("/api/reservations").with(csrf()).with(user("alex@example.com").roles("CUSTOMER")).contentType("application/json")
            .content(json.writeValueAsString(new Api.Booking(LocalDate.now(clock).minusDays(1),LocalTime.NOON,0,"bad",null)))).andExpect(status().isBadRequest());
    }
    @Test void adminTableChangesProtectExistingBookings() throws Exception {
        var r=service.create("alex@example.com",booking(LocalTime.NOON));
        bookingService.submitPayment("alex@example.com",r.id(),"TEST-RECEIPT");
        bookingService.reviewPayment(r.id(),new Api.PaymentReview(true,"Verified in test ledger"));
        mvc.perform(delete("/api/admin/tables/"+r.tableId()).with(csrf()).with(user("manager").roles("ADMIN"))).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/reservations/"+r.id()+"/status").with(csrf()).with(user("manager").roles("ADMIN")).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
    }
    @Test void combinesTablesAndProtectsEveryAssignedTable() {
        tables.deleteAll();var first=tables.save(new RestaurantTable("A",6));var second=tables.save(new RestaurantTable("B",6));
        var input=new Api.Booking(LocalDate.now(clock).plusDays(2),LocalTime.NOON,12,"09171234567",null,List.of(first.getId(),second.getId()),List.of("Wheelchair access"),true);
        var r=service.create("alex@example.com",input);
        assertThat(r.tableIds()).containsExactlyInAnyOrder(first.getId(),second.getId());
        assertThat(r.guestNeeds()).contains("Wheelchair");
        assertThatThrownBy(()->service.create("jamie@example.com",booking(LocalTime.NOON))).hasMessageContaining("filled up");
        service.cancel("alex@example.com",r.id());
        assertThat(service.create("jamie@example.com",input).tableIds()).hasSize(2);
    }
    @Test void autoCombinationNeverUsesSameTableTwice() {
        tables.deleteAll();tables.save(new RestaurantTable("A",6));tables.save(new RestaurantTable("B",6));
        var r=service.create("alex@example.com",new Api.Booking(LocalDate.now(clock).plusDays(2),LocalTime.NOON,12,"09171234567",null));
        assertThat(r.tableIds()).hasSize(2).doesNotHaveDuplicates();
    }
    @Test void rejectsSelectedBusyTableEvenWhenAnotherTableIsFree() {
        var r=service.create("alex@example.com",booking(LocalTime.NOON));tables.save(new RestaurantTable("B",4));
        var input=new Api.Booking(LocalDate.now(clock).plusDays(2),LocalTime.NOON,2,"09171234567",null,r.tableIds(),List.of(),true);
        assertThatThrownBy(()->service.create("jamie@example.com",input)).hasMessageContaining("already reserved");
    }
    @Test void occupancyLimitAppliesEvenIfMorePhysicalTablesExist() {
        tables.deleteAll();tables.save(new RestaurantTable("Large",100));tables.save(new RestaurantTable("Extra",4));
        service.create("alex@example.com",new Api.Booking(LocalDate.now(clock).plusDays(2),LocalTime.NOON,100,"09171234567",null));
        assertThatThrownBy(()->service.create("jamie@example.com",booking(LocalTime.NOON))).hasMessageContaining("100-person");
        assertThat(service.availability(LocalDate.now(clock).plusDays(2),1).stream().filter(s->s.time().equals(LocalTime.NOON)).findFirst().orElseThrow().availableSeats()).isZero();
    }
    @Test void depositRequiresVerificationAndRecordsRefundWithoutMovingMoney() {
        var r=service.create("alex@example.com",booking(LocalTime.NOON));
        assertThat(r.depositAmount()).isEqualByComparingTo("2000");
        assertThatThrownBy(()->service.changeStatus(r.id(),Reservation.Status.CONFIRMED)).hasMessageContaining("deposit");
        bookingService.submitPayment("alex@example.com",r.id(),"REF-123");
        bookingService.reviewPayment(r.id(),new Api.PaymentReview(true,"Matched receipt"));
        service.changeStatus(r.id(),Reservation.Status.CONFIRMED);
        assertThat(service.cancel("alex@example.com",r.id()).depositStatus()).isEqualTo("REFUND_DUE");
        assertThat(bookingService.refund(r.id(),"REFUND-123").depositStatus()).isEqualTo("REFUNDED");
    }
    @Test void onlyAdminsCanRegisterStaffAndInquiriesAreStored() throws Exception {
        var registration=new Api.Register("Staff","staff@example.com","09171234567",password,password);
        mvc.perform(post("/api/admin/accounts").with(csrf()).with(user("alex@example.com").roles("CUSTOMER")).contentType("application/json").content(json.writeValueAsString(registration))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/accounts").with(csrf()).with(user("manager").roles("ADMIN")).contentType("application/json").content(json.writeValueAsString(registration))).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(post("/api/inquiries").with(csrf()).contentType("application/json").content(json.writeValueAsString(new Api.InquiryInput("Visitor","visitor@example.com","09171234567","Is parking available?")))).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/inquiries").with(user("manager").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].message").value("Is parking available?"));
    }
    @Test void alternativeDatesFindNextAvailableDays() {
        assertThat(bookingService.alternatives(LocalDate.now(clock).plusDays(2),4)).hasSize(3);
    }
    @Test void demoCheckoutVerifiesPerPersonPaymentAndRetriesWithoutDuplicateBooking() throws Exception {
        var input=new Api.DemoCheckout(booking(LocalTime.NOON),UUID.randomUUID(),new java.math.BigDecimal("2000"));
        String body=json.writeValueAsString(input);
        for(int retry=0;retry<2;retry++)mvc.perform(post("/api/reservations/demo-checkout").with(csrf()).with(user("alex@example.com").roles("CUSTOMER"))
            .contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.depositStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.depositAmount").value(2000)).andExpect(jsonPath("$.paymentReference").value("GCASH-DEMO-"+input.checkoutId()));
        assertThat(reservations.count()).isEqualTo(1);
        mvc.perform(post("/api/reservations/demo-checkout").with(csrf()).with(user("jamie@example.com").roles("CUSTOMER"))
            .contentType("application/json").content(body)).andExpect(status().isNotFound());
    }
    @Test void checkoutRejectsChangedPriceAndUnavailableTableWithoutRecordingPayment() {
        var wrong=new Api.DemoCheckout(booking(LocalTime.NOON),UUID.randomUUID(),new java.math.BigDecimal("500"));
        assertThatThrownBy(()->bookingService.demoCheckout("alex@example.com",wrong)).hasMessageContaining("deposit changed");
        assertThat(reservations.count()).isZero();
        var input=new Api.DemoCheckout(booking(LocalTime.NOON),UUID.randomUUID(),new java.math.BigDecimal("2000"));
        bookingService.demoCheckout("alex@example.com",input);
        assertThatThrownBy(()->bookingService.demoCheckout("jamie@example.com",new Api.DemoCheckout(input.booking(),UUID.randomUUID(),input.expectedAmount()))).hasMessageContaining("filled up");
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void policyChangesRequireAdminAndDoNotChangeExistingDeposits() throws Exception {
        var r=service.create("alex@example.com",booking(LocalTime.NOON));
        var input=new Api.DepositInput(new java.math.BigDecimal("250"),"GCash demo",true);
        mvc.perform(put("/api/admin/deposit-policy").with(csrf()).with(user("alex@example.com").roles("CUSTOMER"))
            .contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/deposit-policy").with(csrf()).with(user("manager").roles("ADMIN"))
            .contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(250));
        assertThat(service.detail("alex@example.com",r.id()).depositAmount()).isEqualByComparingTo("2000");
        assertThat(service.create("alex@example.com",booking(LocalTime.of(15,0))).depositAmount()).isEqualByComparingTo("1000");
    }
    @Test void layoutCreatesExactly100SeatsAndProtectsUpcomingReservations() throws Exception {
        mvc.perform(post("/api/admin/layout").with(csrf()).with(user("manager").roles("ADMIN"))).andExpect(status().isOk());
        var enabled=tables.findAll().stream().filter(RestaurantTable::isEnabled).toList();
        assertThat(enabled).hasSize(25);assertThat(enabled.stream().mapToInt(RestaurantTable::getCapacity).sum()).isEqualTo(100);
        assertThat(enabled.stream().filter(t->t.getCapacity()==2).count()).isEqualTo(8);
        assertThat(enabled.stream().filter(t->t.getCapacity()==4).count()).isEqualTo(9);
        service.create("alex@example.com",booking(LocalTime.NOON));
        mvc.perform(post("/api/admin/layout").with(csrf()).with(user("manager").roles("ADMIN"))).andExpect(status().isConflict());
    }
}
