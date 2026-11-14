package com.restorant.repository;
import com.restorant.entity.Reservation;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByPaymentReference(String reference);
    @EntityGraph(attributePaths = {"customer", "table"})
    List<Reservation> findAllByOrderByStartsAtDesc();
    @EntityGraph(attributePaths = {"customer", "table"})
    List<Reservation> findByCustomerIdOrderByStartsAtDesc(Long id);
    @Query("select count(distinct r) from Reservation r join r.assignedTables t where t.id = :tableId and r.status in :statuses and r.startsAt < :end and r.endsAt > :start")
    long overlaps(Long tableId, LocalDateTime start, LocalDateTime end, Collection<Reservation.Status> statuses);
    @Query("select (count(r)>0) from Reservation r join r.assignedTables t where t.id=:tableId and r.endsAt>:now and r.status in :statuses")
    boolean existsByTableIdAndEndsAtAfterAndStatusIn(Long tableId, LocalDateTime now, Collection<Reservation.Status> statuses);
    @Query("select distinct r from Reservation r where r.status in :statuses and r.startsAt < :end and r.endsAt > :start")
    List<Reservation> activeDuring(LocalDateTime start,LocalDateTime end,Collection<Reservation.Status> statuses);
}
