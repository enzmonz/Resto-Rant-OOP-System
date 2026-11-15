package com.restorant;

import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.repository.*;
import com.restorant.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationFlowTest extends IntegrationTestBase {
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
        var r=book("alex@example.com",booking(LocalTime.NOON));
        mvc.perform(get("/api/reservations/"+r.id()).with(user("jamie@example.com").roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/reservations/"+r.id()+"/cancel").with(csrf()).with(user("jamie@example.com").roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/reservations/"+r.id()+"/cancel").with(csrf()).with(user("alex@example.com").roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(book("jamie@example.com",booking(LocalTime.NOON)).status()).isEqualTo(Reservation.Status.PENDING);
    }
    @Test void overlapIsRejectedButAdjacentSeatingIsAllowed() {
        book("alex@example.com",booking(LocalTime.NOON));
        assertThatThrownBy(()->book("jamie@example.com",booking(LocalTime.of(13,30)))).hasMessageContaining("filled up");
        assertThat(book("jamie@example.com",booking(LocalTime.of(14,0))).id()).isNotNull();
        assertThat(service.availability(day(),4).stream().filter(s->s.time().equals(LocalTime.NOON)).findFirst().orElseThrow().availableTables()).isZero();
    }
    @Test void concurrentRequestsCannotDoubleBookLastTable() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Boolean>>();
            for(var email:List.of("alex@example.com","jamie@example.com")) tasks.add(pool.submit(()->{start.await();try{book(email,booking(LocalTime.NOON));return true;}catch(com.restorant.exception.ApiException e){if(e.getStatus()!=409)throw e;return false;}}));
            start.countDown();
            int successes=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS))successes++;
            assertThat(successes).isEqualTo(1);assertThat(reservations.count()).isEqualTo(1);
        }
    }
    @Test void invalidTimeAndGuestCountAreRejected() throws Exception {
        assertThatThrownBy(()->book("alex@example.com",booking(LocalTime.of(23,0)))).hasMessageContaining("opening hours");
        mvc.perform(post("/api/reservations").with(csrf()).with(user("alex@example.com").roles("CUSTOMER")).contentType("application/json")
            .content(json.writeValueAsString(new Api.Booking(LocalDate.now(clock).minusDays(1),LocalTime.NOON,0,"bad",null)))).andExpect(status().isBadRequest());
    }
    @Test void adminTableChangesProtectExistingBookings() throws Exception {
        var r=book("alex@example.com",booking(LocalTime.NOON));
        pay(ALEX,r.id(),"1234567890123");
        mvc.perform(delete("/api/admin/tables/"+r.tableId()).with(csrf()).with(user("manager").roles("ADMIN"))).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/reservations/"+r.id()+"/status").with(csrf()).with(user("manager").roles("ADMIN")).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
    }
    @Test void combinesTablesAndProtectsEveryAssignedTable() {
        tables.deleteAll();var first=tables.save(new RestaurantTable("A",6));var second=tables.save(new RestaurantTable("B",6));
        var input=new Api.Booking(day(),LocalTime.NOON,12,"09171234567",null,List.of(first.getId(),second.getId()),List.of("Wheelchair access"),true);
        var r=book("alex@example.com",input);
        assertThat(r.tableIds()).containsExactlyInAnyOrder(first.getId(),second.getId());
        assertThat(r.guestNeeds()).contains("Wheelchair");
        assertThatThrownBy(()->book("jamie@example.com",booking(LocalTime.NOON))).hasMessageContaining("filled up");
        service.cancel("alex@example.com",r.id());
        assertThat(book("jamie@example.com",input).tableIds()).hasSize(2);
    }
    @Test void autoCombinationNeverUsesSameTableTwice() {
        tables.deleteAll();tables.save(new RestaurantTable("A",6));tables.save(new RestaurantTable("B",6));
        var r=book("alex@example.com",new Api.Booking(day(),LocalTime.NOON,12,"09171234567",null));
        assertThat(r.tableIds()).hasSize(2).doesNotHaveDuplicates();
    }
    @Test void rejectsSelectedBusyTableEvenWhenAnotherTableIsFree() {
        var r=book("alex@example.com",booking(LocalTime.NOON));tables.save(new RestaurantTable("B",4));
        var input=new Api.Booking(day(),LocalTime.NOON,2,"09171234567",null,r.tableIds(),List.of(),true);
        assertThatThrownBy(()->book("jamie@example.com",input)).hasMessageContaining("already reserved");
    }
    @Test void occupancyLimitAppliesEvenIfMorePhysicalTablesExist() {
        tables.deleteAll();tables.save(new RestaurantTable("Large",100));tables.save(new RestaurantTable("Extra",4));
        book("alex@example.com",new Api.Booking(day(),LocalTime.NOON,100,"09171234567",null));
        assertThatThrownBy(()->book("jamie@example.com",booking(LocalTime.NOON))).hasMessageContaining("100-person");
        assertThat(service.availability(day(),1).stream().filter(s->s.time().equals(LocalTime.NOON)).findFirst().orElseThrow().availableSeats()).isZero();
    }
    @Test void onlyAdminsCanRegisterStaffAndInquiriesAreStored() throws Exception {
        var registration=new Api.Register("Staff","staff@example.com","09171234567",password,password);
        mvc.perform(post("/api/admin/accounts").with(csrf()).with(user("alex@example.com").roles("CUSTOMER")).contentType("application/json").content(json.writeValueAsString(registration))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/accounts").with(csrf()).with(user("manager").roles("ADMIN")).contentType("application/json").content(json.writeValueAsString(registration))).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(post("/api/inquiries").with(csrf()).contentType("application/json").content(json.writeValueAsString(new Api.InquiryInput("Visitor","visitor@example.com","09171234567","Is parking available?")))).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/inquiries").with(user("manager").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].message").value("Is parking available?"));
    }
    @Test void adminsCanListStaffAccounts() throws Exception {
        users.save(new Admin("Manager","manager@example.com","09171230000",encoder.encode(password)));
        mvc.perform(get("/api/admin/accounts").with(user("alex@example.com").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/accounts").with(user("manager").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value("manager@example.com"));
    }
    private Api.Booking stay(LocalTime time,Integer minutes) { return booking(time,4,minutes,List.of()); }
    @Test void guestsChooseTheirLengthOfStay() {
        var r=book("alex@example.com",stay(LocalTime.NOON,60));
        assertThat(r.endsAt()).isEqualTo(r.startsAt().plusMinutes(60));
        // The one-hour booking frees the table at 1 PM.
        assertThat(book("jamie@example.com",stay(LocalTime.of(13,0),180)).endsAt().toLocalTime()).isEqualTo(LocalTime.of(16,0));
        assertThatThrownBy(()->book("alex@example.com",stay(LocalTime.of(17,0),45))).hasMessageContaining("length of stay");
        // A three-hour stay must end by the 10 PM closing time, so 8 PM is not offered.
        assertThat(service.availability(LocalDate.now(clock).plusDays(3),2,180)).noneMatch(s->s.time().equals(LocalTime.of(20,0)));
        assertThat(service.availability(LocalDate.now(clock).plusDays(3),2,60)).anyMatch(s->s.time().equals(LocalTime.of(21,0)));
    }
    @Test void phoneNumbersAndFarFutureDatesAreRejected() throws Exception {
        var badPhone=new Api.Register("Sam","sam@example.com","12345abc",password,password);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(badPhone))).andExpect(status().isBadRequest());
        var farAway=new Api.Booking(LocalDate.now(clock).plusDays(400),LocalTime.NOON,2,"09171234567",null);
        assertThatThrownBy(()->book("alex@example.com",farAway)).hasMessageContaining("days ahead");
    }
    @Test void alternativeDatesFindNextAvailableDays() {
        assertThat(bookingService.alternatives(day(),4)).hasSize(3);
    }
    @Test void layoutCreatesExactly100SeatsAndProtectsUpcomingReservations() throws Exception {
        mvc.perform(post("/api/admin/layout").with(csrf()).with(user("manager").roles("ADMIN"))).andExpect(status().isOk());
        var enabled=tables.findAll().stream().filter(RestaurantTable::isEnabled).toList();
        assertThat(enabled).hasSize(25);assertThat(enabled.stream().mapToInt(RestaurantTable::getCapacity).sum()).isEqualTo(100);
        assertThat(enabled.stream().filter(t->t.getCapacity()==2).count()).isEqualTo(8);
        assertThat(enabled.stream().filter(t->t.getCapacity()==4).count()).isEqualTo(9);
        book("alex@example.com",booking(LocalTime.NOON));
        mvc.perform(post("/api/admin/layout").with(csrf()).with(user("manager").roles("ADMIN"))).andExpect(status().isConflict());
    }
}
