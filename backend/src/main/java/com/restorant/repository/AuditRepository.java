package com.restorant.repository;
import com.restorant.entity.AuditEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findByReservationIdOrderByIdAsc(Long reservationId);
}
