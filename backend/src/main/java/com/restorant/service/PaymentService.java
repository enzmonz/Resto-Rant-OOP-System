package com.restorant.service;

import com.restorant.config.PaymentSettings;
import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import com.restorant.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Manual GCash payments and refunds.
 *
 * No payment provider is connected, so this service never decides on its own that money arrived or was sent back.
 * A customer's reference number is a claim that stays PENDING; only an administrator who has checked the receiving
 * GCash account can mark it PAID. Refunds are likewise sent by hand in GCash and recorded here afterwards.
 * It never asks for, receives or stores a GCash MPIN or OTP.
 */
@Service @Transactional(readOnly = true)
public class PaymentService {
    /** Largest accepted proof-of-payment image. */
    public static final int MAX_PROOF_BYTES = 3 * 1024 * 1024;
    private static final List<Payment.Status> OPEN_OR_PAID = List.of(Payment.Status.PENDING, Payment.Status.PAID);
    private final PaymentRepository payments;
    private final PaymentProofRepository proofs;
    private final RefundRepository refunds;
    private final ReservationRepository reservations;
    private final AccountService accounts;
    private final BookingMutex mutex;
    private final PaymentSettings settings;
    private final ReservationViews views;
    private final AuditLog audit;
    private final Clock clock;
    public PaymentService(PaymentRepository payments, PaymentProofRepository proofs, RefundRepository refunds, ReservationRepository reservations,
                          AccountService accounts, BookingMutex mutex, PaymentSettings settings, ReservationViews views, AuditLog audit, Clock clock) {
        this.payments=payments; this.proofs=proofs; this.refunds=refunds; this.reservations=reservations; this.accounts=accounts;
        this.mutex=mutex; this.settings=settings; this.views=views; this.audit=audit; this.clock=clock;
    }
    public record Proof(String contentType, byte[] data) {}

    public Api.PaymentInstructions instructions() {
        String number=settings.gcashNumber();
        String display=number.substring(0,4)+" "+number.substring(4,7)+" "+number.substring(7);
        return new Api.PaymentInstructions("MANUAL_GCASH",false,number,display,settings.gcashAccountName(),List.of(
            "Open your own GCash app and choose Send Money.",
            "Send the exact total to "+display+(settings.gcashAccountName().isEmpty()?"":" ("+settings.gcashAccountName()+")")+".",
            "Copy the reference number from the GCash receipt and submit it here. A screenshot of the receipt is optional.",
            "Our staff check the receiving account and mark the payment as paid. Until then it stays pending."));
    }

    // ---- Customer: submit a payment claim ----

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.BookingView submit(String email, Long reservationId, String reference, byte[] proof) {
        var customer=accounts.require(email);
        mutex.acquire();
        var r=reservations.findById(reservationId).filter(x->x.getCustomer().getId().equals(customer.getId()))
            .orElseThrow(()->new ApiException(404,"Reservation not found"));
        String cleaned=cleanReference(reference,"Enter the reference number from your GCash receipt (8 to 30 letters or digits)");
        // A reference that is already pending or paid elsewhere cannot be claimed a second time.
        if(payments.existsByReferenceAndStatusIn(cleaned,OPEN_OR_PAID))
            throw new ApiException(409,"That GCash reference number was already submitted. Check the number on your receipt.");
        String type=proof==null || proof.length==0?null:imageType(proof);
        BigDecimal due=views.money(r).due();
        r.paymentSubmitted();
        var payment=payments.saveAndFlush(new Payment(r,due,cleaned,settings.gcashNumber(),type!=null,clock.instant(),email));
        if(type!=null)proofs.save(new PaymentProof(payment.getId(),type,proof));
        audit.record(r.getId(),payment.getId(),null,"PAYMENT_SUBMITTED","Customer reported a GCash transfer of "+due.toPlainString()+" to "+settings.gcashNumber()
            +", reference "+cleaned+(type!=null?" with proof image":"")+". Not yet verified.",email);
        return views.of(r);
    }
    static String cleanReference(String value, String message) {
        String cleaned=value==null?"":value.replaceAll("[\\s-]","").toUpperCase(Locale.ROOT);
        if(!cleaned.matches("[A-Z0-9]{8,30}"))throw new ApiException(400,message);
        return cleaned;
    }
    /** Accepts PNG, JPEG and WebP only, identified by their file signature rather than the uploaded name or header. */
    static String imageType(byte[] data) {
        if(data.length>MAX_PROOF_BYTES)throw new ApiException(400,"The proof image must be 3 MB or smaller");
        if(data.length>8 && (data[0]&0xFF)==0x89 && data[1]=='P' && data[2]=='N' && data[3]=='G')return "image/png";
        if(data.length>3 && (data[0]&0xFF)==0xFF && (data[1]&0xFF)==0xD8 && (data[2]&0xFF)==0xFF)return "image/jpeg";
        if(data.length>12 && data[0]=='R' && data[1]=='I' && data[2]=='F' && data[3]=='F' && data[8]=='W' && data[9]=='E' && data[10]=='B' && data[11]=='P')return "image/webp";
        throw new ApiException(400,"The proof must be a PNG, JPEG or WebP image");
    }

    // ---- Staff: verify or reject ----

    public List<Api.PaymentView> list(Payment.Status status) {
        var rows=status==null?payments.findAllByOrderBySubmittedAtDesc():payments.findByStatusOrderBySubmittedAtAsc(status);
        return rows.stream().map(Api.PaymentView::of).toList();
    }
    public Proof proof(Long paymentId) {
        return proofs.findById(paymentId).map(p->new Proof(p.getContentType(),p.getData())).orElseThrow(()->new ApiException(404,"No proof image was attached to this payment"));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.PaymentView review(Long paymentId, Api.PaymentReview input, String actor) {
        mutex.acquire();
        var payment=payments.findById(paymentId).orElseThrow(()->new ApiException(404,"Payment not found"));
        if(input.approved() && !input.verifiedAgainstAccount())
            throw new ApiException(400,"Confirm that you found this transfer in the receiving GCash account before marking it paid");
        payment.review(input.approved(),input.note(),clock.instant(),actor);
        payment.getReservation().paymentReviewed(input.approved());
        payments.saveAndFlush(payment);
        audit.record(payment.getReservation().getId(),payment.getId(),null,input.approved()?"PAYMENT_VERIFIED":"PAYMENT_REJECTED",
            (input.approved()?"Staff confirmed "+payment.getAmount().toPlainString()+" was received in "+payment.getReceivingAccount()+" for reference "
                :"Staff could not verify reference ")+payment.getReference()+(payment.getReviewNote()==null?"":". Note: "+payment.getReviewNote()),actor);
        return Api.PaymentView.of(payment);
    }

    // ---- Staff: refunds ----

    public List<Api.RefundView> refunds() { return refunds.findAllByOrderByCreatedAtDesc().stream().map(Api.RefundView::of).toList(); }
    /** Reservations that are owed money under the cancellation policy and have no refund covering it yet. */
    public List<Api.BookingView> refundsDue() {
        var paidReservations=payments.findByStatusOrderBySubmittedAtAsc(Payment.Status.PAID).stream().map(Payment::getReservation).distinct().toList();
        return views.of(paidReservations).stream().filter(v->v.money().refundDue().signum()>0).toList();
    }
    /**
     * Records that a refund is owed and will be sent by hand. The amount can never exceed the verified payment minus
     * every refund already pending or completed, and only one refund per reservation may be open at a time.
     */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.RefundView createRefund(Long reservationId, Api.RefundInput input, String actor) {
        mutex.acquire();
        var r=reservations.findById(reservationId).orElseThrow(()->new ApiException(404,"Reservation not found"));
        var ids=List.of(r.getId());
        var paid=payments.findByReservationIdInOrderByIdAsc(ids);
        var existing=refunds.findByReservationIdInOrderByIdAsc(ids);
        var payment=paid.stream().filter(Payment::isRefundable).findFirst().orElseThrow(()->new ApiException(409,
            paid.stream().anyMatch(p->p.getStatus()==Payment.Status.PAID)
                ?"This reservation was paid in the old classroom simulation. No money was received, so there is nothing to refund."
                :"There is no verified payment to refund for this reservation"));
        if(existing.stream().anyMatch(f->f.getStatus()==Refund.Status.PENDING))
            throw new ApiException(409,"A refund for this reservation is already pending. Complete or void it before recording another.");
        BigDecimal max=ReservationViews.money(r,paid,existing).maxRefundable();
        if(input.amount().compareTo(max)>0)
            throw new ApiException(409,"The refund exceeds what can still be refunded for this reservation ("+max.toPlainString()+").");
        var refund=refunds.saveAndFlush(new Refund(payment,input.amount(),input.reason(),clock.instant(),actor));
        audit.record(r.getId(),payment.getId(),refund.getId(),"REFUND_CREATED","Refund of "+refund.getAmount().toPlainString()+" to be sent manually for payment reference "
            +refund.getPaymentReference()+". Reason: "+refund.getReason(),actor);
        return Api.RefundView.of(refund);
    }
    /** Staff record the GCash reference of a refund they already sent by hand. The application sends nothing itself. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.RefundView completeRefund(Long refundId, Api.RefundCompletion input, String actor) {
        mutex.acquire();
        var refund=refunds.findById(refundId).orElseThrow(()->new ApiException(404,"Refund not found"));
        String reference=cleanReference(input.refundReference(),"Enter the reference number of the GCash refund you sent (8 to 30 letters or digits)");
        if(refunds.existsByRefundReference(reference))throw new ApiException(409,"That refund reference is already recorded on another refund");
        refund.complete(reference,input.note(),clock.instant(),actor);
        refunds.saveAndFlush(refund);
        audit.record(refund.getReservation().getId(),refund.getPayment().getId(),refund.getId(),"REFUND_COMPLETED","Staff recorded a manual GCash refund of "
            +refund.getAmount().toPlainString()+", refund reference "+reference+(refund.getNote()==null?"":". Note: "+refund.getNote()),actor);
        return Api.RefundView.of(refund);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Api.RefundView voidRefund(Long refundId, String note, String actor) {
        mutex.acquire();
        var refund=refunds.findById(refundId).orElseThrow(()->new ApiException(404,"Refund not found"));
        refund.cancel(note,clock.instant(),actor);
        refunds.saveAndFlush(refund);
        audit.record(refund.getReservation().getId(),refund.getPayment().getId(),refund.getId(),"REFUND_VOIDED","Pending refund of "
            +refund.getAmount().toPlainString()+" voided; no money was sent. Note: "+refund.getNote(),actor);
        return Api.RefundView.of(refund);
    }
    public List<Api.AuditView> history(Long reservationId) {
        if(!reservations.existsById(reservationId))throw new ApiException(404,"Reservation not found");
        return audit.history(reservationId);
    }
    public Map<String,Long> counts() {
        return Map.of("pendingPayments",payments.countByStatus(Payment.Status.PENDING),"pendingRefunds",refunds.countByStatus(Refund.Status.PENDING),
            "refundsDue",(long)refundsDue().size());
    }
}
