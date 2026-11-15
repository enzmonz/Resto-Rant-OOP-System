package com.restorant.service;

import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import com.restorant.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Reservation package prices. Each table is priced by its seating capacity and a reservation costs the sum of
 * the packages of its tables. Prices are read here on the server; nothing a browser sends can replace them.
 */
@Service @Transactional(readOnly = true)
public class PricingService {
    private final TablePackageRepository packages;
    private final TableRepository tables;
    private final BookingMutex mutex;
    private final AuditLog audit;
    private final Clock clock;
    public PricingService(TablePackageRepository packages, TableRepository tables, BookingMutex mutex, AuditLog audit, Clock clock) {
        this.packages=packages; this.tables=tables; this.mutex=mutex; this.audit=audit; this.clock=clock;
    }
    /** Price lookup for one request, so every table in it is priced from the same snapshot of the price list. */
    public PriceList priceList() { return new PriceList(packages.findAllByOrderByCapacityAsc()); }
    public BigDecimal priceFor(int capacity) { return priceList().priceFor(capacity); }

    public static final class PriceList {
        private final TreeMap<Integer,BigDecimal> prices=new TreeMap<>();
        PriceList(List<TablePackage> configured) { for(var p:configured)prices.put(p.getCapacity(),p.getPrice()); }
        public boolean isConfigured(int capacity) { return prices.containsKey(capacity); }
        /**
         * The configured package for that capacity. A capacity without its own package (for example a table an
         * administrator added later) is priced at the per-seat rate of the nearest smaller package, rounded up to a peso.
         */
        public BigDecimal priceFor(int capacity) {
            var exact=prices.get(capacity);
            if(exact!=null)return exact;
            if(prices.isEmpty())throw new ApiException(503,"Reservation package prices are not set up yet. Please contact the restaurant.");
            var basis=prices.floorEntry(capacity)!=null?prices.floorEntry(capacity):prices.firstEntry();
            return basis.getValue().multiply(BigDecimal.valueOf(capacity)).divide(BigDecimal.valueOf(basis.getKey()),0,RoundingMode.CEILING).setScale(2);
        }
        public BigDecimal total(Collection<RestaurantTable> selected) {
            return selected.stream().map(t->priceFor(t.getCapacity())).reduce(BigDecimal.ZERO.setScale(2),BigDecimal::add);
        }
    }
    /** Every configured package plus every capacity currently used by an enabled table. */
    public List<Api.PackageView> packages() {
        var list=priceList();
        var inUse=new TreeMap<Integer,Long>();
        for(var t:tables.findAll())if(t.isEnabled())inUse.merge(t.getCapacity(),1L,Long::sum);
        var capacities=new TreeSet<>(list.prices.keySet());capacities.addAll(inUse.keySet());
        return capacities.stream().map(c->new Api.PackageView(c,list.priceFor(c),list.isConfigured(c),inUse.getOrDefault(c,0L))).toList();
    }
    /** Changes apply to new reservations only; existing reservations keep the price snapshot they were booked with. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.PackageView update(int capacity, BigDecimal price, String actor) {
        if(capacity<1 || capacity>100)throw new ApiException(400,"Seating capacity must be 1 to 100");
        mutex.acquire();
        var existing=packages.findById(capacity);
        String before=existing.map(p->p.getPrice().toPlainString()).orElse("default");
        var saved=existing.orElseGet(()->new TablePackage(capacity,price,clock.instant(),actor));
        saved.reprice(price,clock.instant(),actor);packages.save(saved);
        audit.record(null,null,null,"PRICE_CHANGED",capacity+"-seat table package: "+before+" -> "+saved.getPrice().toPlainString(),actor);
        long count=tables.findAll().stream().filter(t->t.isEnabled() && t.getCapacity()==capacity).count();
        return new Api.PackageView(capacity,saved.getPrice(),true,count);
    }
}
