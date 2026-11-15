package com.restorant;

import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Timezone independence, table capacity, overlap boundaries, lengths of stay and concurrent bookings. */
class BookingRulesTest extends IntegrationTestBase {
    @Autowired JdbcTemplate jdbc;

    // ---- Timezone ----

    @Test void storedReservationTimesDoNotMoveWhenTheServerTimezoneChanges() {
        var original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var r=book(ALEX,booking(LocalTime.NOON));
            // Noon in Manila is 04:00 UTC, whatever timezone the computer was set to when the booking was saved.
            LocalDateTime raw=jdbc.queryForObject("SELECT starts_at FROM reservations WHERE id="+r.id(),(rs,row)->rs.getObject(1,LocalDateTime.class));
            assertThat(raw).isEqualTo(day().atTime(4,0));
            for(String zone:List.of("Asia/Tokyo","UTC","Pacific/Auckland","Asia/Manila")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                var stored=service.detail(ALEX,r.id());
                assertThat(stored.startsAt()).as("start read in %s",zone).isEqualTo(day().atTime(LocalTime.NOON));
                assertThat(stored.endsAt()).isEqualTo(day().atTime(14,0));
                var slots=service.availability(day(),4,120);
                assertThat(slot(slots,LocalTime.NOON).availableTables()).as("noon is taken in %s",zone).isZero();
                assertThat(slot(slots,LocalTime.of(14,0)).availableTables()).as("2 PM is free in %s",zone).isEqualTo(1);
            }
        } finally { TimeZone.setDefault(original); }
    }
    @Test void todayAndTheBookingWindowComeFromTheRestaurantClockNotTheMachineTimezone() throws Exception {
        var original=TimeZone.getDefault();
        try {
            // UTC+14 and UTC-11: whichever date these zones are on, the restaurant's "today" stays Manila's.
            for(String zone:List.of("Pacific/Kiritimati","Pacific/Pago_Pago")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                var manilaToday=LocalDate.now(MANILA);
                mvc.perform(get("/api/info")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.today").value(manilaToday.toString()))
                    .andExpect(jsonPath("$.zone").value("Asia/Manila")).andExpect(jsonPath("$.utcOffset").value("+08:00"))
                    .andExpect(jsonPath("$.lastBookableDate").value(manilaToday.plusDays(365).toString()));
                assertThatThrownBy(()->service.availability(manilaToday.minusDays(1),2)).hasMessageContaining("current or future date");
                assertThat(service.availability(manilaToday.plusDays(1),2)).isNotEmpty();
            }
        } finally { TimeZone.setDefault(original); }
    }
    @Test void slotsAlreadyPastInManilaAreNotOfferedForToday() {
        clock.setNow(day().atTime(15,10));
        var slots=service.availability(day(),2,60);
        assertThat(slots).noneMatch(s->!s.time().isAfter(LocalTime.of(15,10)));
        assertThat(slots.getFirst().time()).isEqualTo(LocalTime.of(15,30));
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.of(15,0),2,60,List.of()))).hasMessageContaining("future");
    }
    @Test void converterMapsRestaurantWallTimeToTheSameInstantUnderAnyDefaultTimezone() {
        var converter=new RestaurantTimeConverter(new com.restorant.config.RestaurantSettings(MANILA,LocalTime.of(11,0),LocalTime.of(22,0),120,30,100,2));
        var original=TimeZone.getDefault();
        try {
            for(String zone:List.of("America/New_York","Asia/Kolkata","UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                var stored=converter.convertToDatabaseColumn(LocalDateTime.of(2030,1,2,12,0));
                assertThat(stored.toInstant()).isEqualTo(Instant.parse("2030-01-02T04:00:00Z"));
                assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(LocalDateTime.of(2030,1,2,12,0));
            }
        } finally { TimeZone.setDefault(original); }
    }

    // ---- Capacity ----

    @Test void aHundredGuestsAreSeatedAcrossTablesThatAddUpToAHundredSeats() {
        diningRoom();
        var r=book(ALEX,booking(LocalTime.NOON,100,120,List.of()));
        assertThat(r.tableIds()).hasSize(25).doesNotHaveDuplicates();
        assertThat(r.seats()).isEqualTo(100);
        assertThat(r.items()).hasSize(25);
        assertThat(slot(service.availability(day(),1,120),LocalTime.NOON).availableSeats()).isZero();
    }
    @Test void aHundredGuestsAreNeverAcceptedOnOneTableOrOnTooFewSeats() {
        diningRoom();
        var six=tables.findAll().stream().filter(t->t.getCapacity()==6).toList();
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.NOON,100,120,List.of(six.getFirst().getId())))).hasMessageContaining("too few seats");
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.NOON,13,120,List.of(six.get(0).getId(),six.get(1).getId())))).hasMessageContaining("too few seats");
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.NOON,2,120,List.of(six.get(0).getId(),six.get(0).getId())))).hasMessageContaining("only once");
        assertThat(reservations.count()).isZero();
        // The domain object enforces the same rule even without the service.
        var customer=(Customer)users.findByEmail(ALEX).orElseThrow();
        assertThatThrownBy(()->new Reservation(customer,List.of(six.getFirst()),day().atTime(12,0),day().atTime(14,0),100,null,clock))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seat every guest");
    }
    @Test void automaticSelectionUsesOnlyFreeTablesAndFailsWhenTheyCannotSeatTheParty() {
        diningRoom();
        var first=book(JAMIE,booking(LocalTime.NOON,2,120,List.of()));
        assertThat(first.tableIds()).hasSize(1);
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.NOON,100,120,List.of()))).isInstanceOf(ApiException.class);
        assertThat(slot(service.availability(day(),100,120),LocalTime.NOON).availableTables()).isZero();
        var big=book(ALEX,booking(LocalTime.NOON,98,120,List.of()));
        assertThat(big.seats()).isEqualTo(98);
        assertThat(big.tableIds()).hasSize(24).doesNotContainAnyElementsOf(first.tableIds());
        // A different time on the same day still has every table.
        assertThat(slot(service.availability(day(),100,120),LocalTime.of(15,0)).availableTables()).isEqualTo(25);
    }
    @Test void assignTablesForMeSuggestsExactlyTheTablesTheServerBooks() {
        diningRoom();
        var map=bookingService.seating(day(),LocalTime.NOON,10,120);
        var suggested=map.stream().filter(Api.TableView::suggested).toList();
        assertThat(suggested.stream().mapToInt(Api.TableView::capacity).sum()).isEqualTo(10);
        assertThat(suggested).allMatch(Api.TableView::available);
        var ids=suggested.stream().map(Api.TableView::id).toList();
        // Submitting the highlighted tables books those tables; leaving the choice to the server picks the same ones.
        assertThat(book(ALEX,booking(LocalTime.NOON,10,120,ids)).tableIds()).containsExactlyInAnyOrderElementsOf(ids);
        var later=bookingService.seating(day(),LocalTime.of(15,0),10,120).stream().filter(Api.TableView::suggested).map(Api.TableView::id).toList();
        assertThat(book(JAMIE,booking(LocalTime.of(15,0),10,120,List.of())).tableIds()).containsExactlyInAnyOrderElementsOf(later);
        // Tables taken at noon are never suggested again for an overlapping stay.
        assertThat(bookingService.seating(day(),LocalTime.of(13,0),10,60).stream().filter(Api.TableView::suggested).map(Api.TableView::id)).doesNotContainAnyElementsOf(ids);
    }
    @Test void concurrentLargePartiesCannotShareSeats() throws Exception {
        diningRoom();
        var results=race(()->book(ALEX,booking(LocalTime.NOON,60,120,List.of())),()->book(JAMIE,booking(LocalTime.NOON,60,120,List.of())));
        assertThat(results.stream().filter(Objects::nonNull)).hasSize(1);
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void concurrentAutomaticBookingsReceiveDifferentTables() throws Exception {
        tables.save(new RestaurantTable("T02",4));
        var results=race(()->book(ALEX,booking(LocalTime.NOON,4,120,List.of())),()->book(JAMIE,booking(LocalTime.NOON,4,120,List.of())));
        assertThat(results).doesNotContainNull();
        assertThat(results.get(0).tableIds()).doesNotContainAnyElementsOf(results.get(1).tableIds());
    }
    private List<Api.BookingView> race(Callable<Api.BookingView> first,Callable<Api.BookingView> second) throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Api.BookingView>>();
            for(var attempt:List.of(first,second))tasks.add(pool.submit(()->{start.await();try{return attempt.call();}catch(ApiException e){if(e.getStatus()!=409)throw e;return null;}}));
            start.countDown();
            var results=new ArrayList<Api.BookingView>();for(var task:tasks)results.add(task.get(20,TimeUnit.SECONDS));
            return results;
        }
    }

    // ---- Overlap boundaries and lengths of stay ----

    @Test void aStartTimeIsOfferedOnlyWhenTheWholeStayIsFree() {
        book(JAMIE,booking(LocalTime.of(13,0),4,120,List.of()));   // T01 is taken 1:00 PM to 3:00 PM
        var oneHour=service.availability(day(),4,60);
        assertThat(slot(oneHour,LocalTime.of(12,0)).availableTables()).as("12:00 to 1:00 ends as the next booking starts").isEqualTo(1);
        assertThat(slot(oneHour,LocalTime.of(12,0)).endTime()).isEqualTo(LocalTime.of(13,0));
        assertThat(slot(oneHour,LocalTime.of(12,30)).availableTables()).as("12:30 to 1:30 runs into the booking").isZero();
        assertThat(slot(oneHour,LocalTime.of(14,30)).availableTables()).as("2:30 starts before the table is released").isZero();
        assertThat(slot(oneHour,LocalTime.of(15,0)).availableTables()).as("3:00 starts as the booking ends").isEqualTo(1);
        // Changing the length of stay recalculates the same start times.
        assertThat(slot(service.availability(day(),4,90),LocalTime.of(12,0)).availableTables()).isZero();
        assertThat(slot(service.availability(day(),4,90),LocalTime.of(11,30)).availableTables()).isEqualTo(1);
        assertThat(slot(service.availability(day(),4,120),LocalTime.of(11,0)).availableTables()).isEqualTo(1);
        assertThat(slot(service.availability(day(),4,120),LocalTime.of(11,30)).availableTables()).isZero();
        // The table map applies the same rule as the time list.
        assertThat(bookingService.seating(day(),LocalTime.of(12,0),4,60)).allMatch(Api.TableView::available);
        assertThat(bookingService.seating(day(),LocalTime.of(12,0),4,90)).noneMatch(Api.TableView::available);
        // And so does the booking itself.
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.of(12,0),4,90,List.of()))).hasMessageContaining("filled up");
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.of(14,30),4,60,List.of()))).hasMessageContaining("filled up");
        assertThat(book(ALEX,booking(LocalTime.of(12,0),4,60,List.of())).endsAt()).isEqualTo(day().atTime(13,0));
        assertThat(book(ALEX,booking(LocalTime.of(15,0),4,60,List.of())).startsAt()).isEqualTo(day().atTime(15,0));
    }
    @Test void aOneHourStayIsNotOfferedInAThirtyMinuteGap() {
        book(JAMIE,booking(LocalTime.of(11,0),4,60,List.of()));    // 11:00 to 12:00
        book(JAMIE,booking(LocalTime.of(12,30),4,120,List.of()));  // 12:30 to 2:30, leaving only 12:00 to 12:30 free
        for(int minutes:List.of(60,90,120,150,180)) {
            var noon=slot(service.availability(day(),4,minutes),LocalTime.NOON);
            assertThat(noon.availableTables()).as("%d-minute stay at noon",minutes).isZero();
            assertThat(noon.availableSeats()).isZero();
            assertThatThrownBy(()->book(ALEX,booking(LocalTime.NOON,4,minutes,List.of()))).hasMessageContaining("filled up");
            var table=tables.findAll().getFirst().getId();
            assertThatThrownBy(()->book(ALEX,booking(LocalTime.NOON,4,minutes,List.of(table)))).hasMessageContaining("already reserved");
        }
        assertThat(reservations.count()).isEqualTo(2);
        assertThat(slot(service.availability(day(),4,60),LocalTime.of(14,30)).availableTables()).isEqualTo(1);
    }
    @Test void everyOfferedStartTimeEndsByClosing() {
        for(int minutes:List.of(60,90,120,150,180)) {
            var slots=service.availability(day(),2,minutes);
            assertThat(slots).allMatch(s->!s.endTime().isAfter(LocalTime.of(22,0)) && s.endTime().equals(s.time().plusMinutes(minutes)));
            assertThat(slots.getLast().time()).isEqualTo(LocalTime.of(22,0).minusMinutes(minutes));
            assertThat(slots.getFirst().time()).isEqualTo(LocalTime.of(11,0));
        }
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.of(21,30),2,60,List.of()))).hasMessageContaining("opening hours");
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.of(19,30),2,180,List.of()))).hasMessageContaining("opening hours");
        assertThatThrownBy(()->book(ALEX,booking(LocalTime.of(12,15),2,60,List.of()))).hasMessageContaining("seating times");
        assertThat(book(ALEX,booking(LocalTime.of(21,0),2,60,List.of())).endsAt()).isEqualTo(day().atTime(22,0));
    }
    @Test void availabilityEndpointRecalculatesForTheRequestedDuration() throws Exception {
        book(JAMIE,booking(LocalTime.of(13,0),4,120,List.of()));
        mvc.perform(get("/api/availability").param("date",day().toString()).param("guests","4").param("duration","60"))
            .andExpect(jsonPath("$[?(@.time=='12:00:00')].availableTables").value(1));
        mvc.perform(get("/api/availability").param("date",day().toString()).param("guests","4").param("duration","90"))
            .andExpect(jsonPath("$[?(@.time=='12:00:00')].availableTables").value(0));
        mvc.perform(get("/api/availability").param("date",day().toString()).param("guests","4").param("duration","45")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/availability/tables").param("date",day().toString()).param("time","12:00").param("guests","4").param("duration","90")
            .with(user(ALEX).roles("CUSTOMER"))).andExpect(jsonPath("$[0].available").value(false)).andExpect(jsonPath("$[0].price").value(1100.00));
    }
    @Test void cancelledAndPartlyCancelledTablesAreReleasedForOthers() {
        tables.save(new RestaurantTable("T02",4));
        var ids=tables.findAll().stream().map(RestaurantTable::getId).toList();
        var r=book(JAMIE,booking(LocalTime.NOON,4,120,ids));
        assertThat(slot(service.availability(day(),4,120),LocalTime.NOON).availableTables()).isZero();
        service.cancelTables(JAMIE,r.id(),new Api.CancelTables(List.of(ids.get(1)),null));
        assertThat(slot(service.availability(day(),4,120),LocalTime.NOON).availableTables()).isEqualTo(1);
        assertThat(book(ALEX,booking(LocalTime.NOON,4,120,List.of())).tableIds()).containsExactly(ids.get(1));
    }
}
