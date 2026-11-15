package com.restorant.repository;
import com.restorant.entity.Refund;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByReservationIdInOrderByIdAsc(Collection<Long> reservationIds);
    List<Refund> findAllByOrderByCreatedAtDesc();
    boolean existsByRefundReference(String refundReference);
    long countByStatus(Refund.Status status);
}
