package com.restorant.repository;
import com.restorant.entity.Payment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByReservationIdInOrderByIdAsc(Collection<Long> reservationIds);
    List<Payment> findByStatusOrderBySubmittedAtAsc(Payment.Status status);
    List<Payment> findAllByOrderBySubmittedAtDesc();
    boolean existsByReferenceAndStatusIn(String reference, Collection<Payment.Status> statuses);
    long countByStatus(Payment.Status status);
}
