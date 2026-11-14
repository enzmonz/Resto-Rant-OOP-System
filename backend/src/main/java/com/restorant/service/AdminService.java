package com.restorant.service;
import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import com.restorant.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(readOnly=true)
public class AdminService {
    private final TableRepository tables;
    private final UserRepository users;
    private final ReservationRepository reservations;
    private final BookingMutex mutex;
    private final Clock clock;
    public AdminService(TableRepository tables, UserRepository users, ReservationRepository reservations, BookingMutex mutex, Clock clock) {
        this.tables=tables; this.users=users; this.reservations=reservations; this.mutex=mutex; this.clock=clock;
    }
    public List<Api.Account> customers(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return users.findAll().stream().filter(u -> u instanceof Customer)
            .filter(u -> (u.getFullName()+" "+u.getEmail()+" "+u.getPhoneNumber()).toLowerCase(Locale.ROOT).contains(q)).map(Api.Account::of).toList();
    }
    public List<Api.TableView> tables(LocalDateTime at, int duration) {
        var start = at == null ? LocalDateTime.now(clock) : at;
        return tables.findAllByOrderByCapacityAscIdAsc().stream().map(t -> new Api.TableView(t.getId(),t.getTableNumber(),t.getCapacity(),t.isEnabled(),
            t.isEnabled() && reservations.overlaps(t.getId(),start,start.plusMinutes(duration),BookingService.ACTIVE)==0)).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.TableView save(Long id, Api.TableInput input) {
        mutex.acquire();
        var t = id == null ? new RestaurantTable(input.tableNumber(),input.capacity()) : tables.findById(id).orElseThrow(() -> new ApiException(404,"Table not found"));
        if (id != null && (input.capacity() < t.getCapacity() || !input.enabled())
            && reservations.existsByTableIdAndEndsAtAfterAndStatusIn(id,LocalDateTime.now(clock),BookingService.ACTIVE))
            throw new ApiException(409,"Cancel or finish this table's upcoming reservations before reducing capacity or disabling it");
        t.update(input.tableNumber(),input.capacity()); t.setEnabled(input.enabled()); tables.saveAndFlush(t);
        return new Api.TableView(t.getId(),t.getTableNumber(),t.getCapacity(),t.isEnabled(),false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void disable(Long id) {
        var t = tables.findById(id).orElseThrow(() -> new ApiException(404,"Table not found"));
        save(id,new Api.TableInput(t.getTableNumber(),t.getCapacity(),false));
    }
    public Map<String,Long> overview(int duration) {
        var all=reservations.findAll();
        var result=new LinkedHashMap<String,Long>(); result.put("totalReservations",(long)all.size());
        for(var status: Reservation.Status.values()) result.put(status.name().toLowerCase(Locale.ROOT),all.stream().filter(r -> r.getStatus()==status).count());
        result.put("totalCustomers", (long)customers("").size());
        result.put("availableTables",tables(null,duration).stream().filter(Api.TableView::available).count());
        result.put("totalTables",tables.count()); return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void applyLayout() {
        mutex.acquire();
        if(reservations.findAll().stream().anyMatch(r->BookingService.ACTIVE.contains(r.getStatus()) && r.getEndsAt().isAfter(LocalDateTime.now(clock))))
            throw new ApiException(409,"Finish or cancel future reservations before replacing the dining-room layout");
        var existing=tables.findAll();existing.forEach(t->t.setEnabled(false));tables.saveAll(existing);tables.flush();
        for(int i=1;i<=25;i++) {
            String number="D"+String.format("%02d",i);int capacity=i<=8?2:i<=17?4:6;
            var table=existing.stream().filter(t->t.getTableNumber().equals(number)).findFirst().orElseGet(()->new RestaurantTable(number,capacity));
            table.update(number,capacity);table.setEnabled(true);tables.save(table);
        }
    }
}
