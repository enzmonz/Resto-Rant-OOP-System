package com.restorant.service;

import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.repository.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds the reservation responses, including the money summary derived from payments and refunds. */
@Component
public class ReservationViews {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final CancellationPolicy policy;
    public ReservationViews(PaymentRepository payments, RefundRepository refunds, CancellationPolicy policy) {
        this.payments=payments; this.refunds=refunds; this.policy=policy;
    }
    public Api.BookingView of(Reservation r) { return of(List.of(r)).getFirst(); }
    public List<Api.BookingView> of(List<Reservation> rows) {
        if(rows.isEmpty())return List.of();
        var ids=rows.stream().map(Reservation::getId).toList();
        var paid=payments.findByReservationIdInOrderByIdAsc(ids).stream().collect(Collectors.groupingBy(p->p.getReservation().getId()));
        var back=refunds.findByReservationIdInOrderByIdAsc(ids).stream().collect(Collectors.groupingBy(f->f.getReservation().getId()));
        return rows.stream().map(r->build(r,paid.getOrDefault(r.getId(),List.of()),back.getOrDefault(r.getId(),List.of()))).toList();
    }
    public Api.Money money(Reservation r) {
        var ids=List.of(r.getId());
        return money(r,payments.findByReservationIdInOrderByIdAsc(ids),refunds.findByReservationIdInOrderByIdAsc(ids));
    }
    /**
     * ABSTRACTION: one definition of what a reservation owes and is owed.
     * Refunds are capped twice: by what the policy made refundable, and by money staff actually verified.
     */
    public static Api.Money money(Reservation r, List<Payment> payments, List<Refund> refunds) {
        BigDecimal zero=BigDecimal.ZERO.setScale(2);
        BigDecimal paid=payments.stream().filter(p->p.getStatus()==Payment.Status.PAID).map(Payment::getAmount).reduce(zero,BigDecimal::add);
        BigDecimal verified=payments.stream().filter(Payment::isRefundable).map(Payment::getAmount).reduce(zero,BigDecimal::add);
        BigDecimal refunded=refunds.stream().filter(f->f.getStatus()==Refund.Status.COMPLETED).map(Refund::getAmount).reduce(zero,BigDecimal::add);
        BigDecimal pending=refunds.stream().filter(f->f.getStatus()==Refund.Status.PENDING).map(Refund::getAmount).reduce(zero,BigDecimal::add);
        BigDecimal used=refunded.add(pending);
        BigDecimal policyRefundable=r.getItems().stream().filter(i->i.getStatus()==ReservationItem.Status.CANCELLED)
            .map(ReservationItem::getRefundableAmount).reduce(zero,BigDecimal::add);
        BigDecimal maxRefundable=verified.subtract(used).max(zero);
        BigDecimal refundDue=policyRefundable.min(verified).subtract(used).max(zero);
        boolean owes=(r.getStatus()==Reservation.Status.PENDING || r.getStatus()==Reservation.Status.CONFIRMED)
            && (r.getPaymentStatus()==Reservation.PaymentStatus.UNPAID || r.getPaymentStatus()==Reservation.PaymentStatus.REJECTED);
        return new Api.Money(r.getTotalAmount(),r.activeTotal().setScale(2),owes?r.activeTotal().setScale(2):zero,paid,refunded,pending,refundDue,maxRefundable);
    }
    private Api.BookingView build(Reservation r, List<Payment> paid, List<Refund> back) {
        var active=r.activeItems();
        var seated=r.getAssignedTables().stream().sorted(java.util.Comparator.comparing(RestaurantTable::getTableNumber)).toList();
        boolean cancellable=policy.customerMayCancel(r);
        return new Api.BookingView(r.getId(), r.getReferenceNumber(), r.getCustomer().getId(), r.getCustomer().getFullName(),
            r.getCustomer().getEmail(), r.getContactNumber(), r.getTable().getId(),
            String.join(", ",seated.stream().map(RestaurantTable::getTableNumber).toList()),
            r.getStartsAt(), r.getEndsAt(), (int)Duration.between(r.getStartsAt(),r.getEndsAt()).toMinutes(), r.getGuestCount(),
            r.getAssignedTables().stream().mapToInt(RestaurantTable::getCapacity).sum(), r.getSpecialRequests(), r.getStatus(),
            cancellable, cancellable && active.size()>1,
            seated.stream().map(RestaurantTable::getId).toList(), r.getGuestNeeds(),
            r.getItems().stream().map(Api.ItemView::of).toList(), r.getPaymentStatus(), money(r,paid,back),
            paid.stream().map(Api.PaymentView::of).toList(), back.stream().map(Api.RefundView::of).toList());
    }
}
