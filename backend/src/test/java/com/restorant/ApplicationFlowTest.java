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
    private final String password="Test-password-42";
    @BeforeEach void clean() {
        reservations.deleteAll(); users.deleteAll(); tables.deleteAll();
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
        mvc.perform(delete("/api/admin/tables/"+r.tableId()).with(csrf()).with(user("manager").roles("ADMIN"))).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/reservations/"+r.id()+"/status").with(csrf()).with(user("manager").roles("ADMIN")).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
    }
}
