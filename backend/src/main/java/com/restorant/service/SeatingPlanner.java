package com.restorant.service;

import com.restorant.entity.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SeatingPlanner {
    public static final int MAX_OCCUPANCY=100;
    // ABSTRACTION: Allocation is independent of HTTP and persistence and can be tested with domain objects.
    public List<RestaurantTable> bestFit(List<RestaurantTable> available,int guests) {
        Map<Integer,List<RestaurantTable>> combinations=new TreeMap<>();combinations.put(0,List.of());
        for(var table:available) {
            for(var entry:new TreeMap<>(combinations).entrySet()) {
                int seats=entry.getKey()+table.getCapacity();
                if(seats>200)continue;
                var group=new ArrayList<>(entry.getValue());group.add(table);
                if(!combinations.containsKey(seats) || group.size()<combinations.get(seats).size())combinations.put(seats,List.copyOf(group));
            }
        }
        return combinations.entrySet().stream().filter(e->e.getKey()>=guests).map(Map.Entry::getValue).findFirst().orElse(List.of());
    }
    public int remainingCapacity(List<Reservation> existing,LocalDateTime start,LocalDateTime end) {
        var changes=new TreeMap<LocalDateTime,Integer>();
        for(var r:existing) {
            var from=r.getStartsAt().isBefore(start)?start:r.getStartsAt();
            var to=r.getEndsAt().isAfter(end)?end:r.getEndsAt();
            changes.merge(from,r.getGuestCount(),Integer::sum);changes.merge(to,-r.getGuestCount(),Integer::sum);
        }
        int current=0,peak=0;for(int change:changes.values()){current+=change;peak=Math.max(peak,current);}
        return Math.max(0,MAX_OCCUPANCY-peak);
    }
}
