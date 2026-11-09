package com.restorant.repository;
import com.restorant.entity.Reservation;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @EntityGraph(attributePaths = {"customer", "table"})
    List<Reservation> findAllByOrderByStartsAtDesc();
    @EntityGraph(attributePaths = {"customer", "table"})
    List<Reservation> findByCustomerIdOrderByStartsAtDesc(Long id);
    @Query("select count(r) from Reservation r where r.table.id = :tableId and r.status in :statuses and r.startsAt < :end and r.endsAt > :start")
    long overlaps(Long tableId, LocalDateTime start, LocalDateTime end, Collection<Reservation.Status> statuses);
    boolean existsByTableIdAndEndsAtAfterAndStatusIn(Long tableId, LocalDateTime now, Collection<Reservation.Status> statuses);
}
