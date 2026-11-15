package com.restorant.service;

import com.restorant.dto.Api;
import com.restorant.entity.AuditEntry;
import com.restorant.repository.AuditRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/** Writes the append-only payment and refund history inside the caller's transaction. */
@Component
public class AuditLog {
    private final AuditRepository entries;
    private final Clock clock;
    public AuditLog(AuditRepository entries, Clock clock) { this.entries=entries; this.clock=clock; }
    public void record(Long reservationId, Long paymentId, Long refundId, String action, String detail, String actor) {
        entries.save(new AuditEntry(reservationId,paymentId,refundId,action,detail,actor,clock.instant()));
    }
    public List<Api.AuditView> history(Long reservationId) {
        return entries.findByReservationIdOrderByIdAsc(reservationId).stream().map(Api.AuditView::of).toList();
    }
}
