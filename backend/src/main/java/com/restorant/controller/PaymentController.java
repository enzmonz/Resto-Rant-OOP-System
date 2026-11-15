package com.restorant.controller;

import com.restorant.dto.Api;
import com.restorant.entity.Payment;
import com.restorant.service.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Package prices, manual GCash payments and refunds.
 * Customers can only submit a payment claim; marking it paid and every refund step is restricted to /api/admin.
 */
@RestController @RequestMapping("/api")
public class PaymentController {
    private final PricingService pricing;
    private final PaymentService payments;
    private final CancellationPolicy policy;
    public PaymentController(PricingService pricing, PaymentService payments, CancellationPolicy policy) {
        this.pricing=pricing; this.payments=payments; this.policy=policy;
    }
    @GetMapping("/pricing") public Api.Pricing pricing() { return new Api.Pricing(pricing.packages(),payments.instructions(),policy.terms()); }
    @PutMapping("/admin/pricing/{capacity}")
    public Api.PackageView price(Principal staff,@PathVariable int capacity,@Valid @RequestBody Api.PriceInput input) { return pricing.update(capacity,input.price(),staff.getName()); }

    @PostMapping(value="/reservations/{id}/payments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Api.BookingView submit(Principal user,@PathVariable Long id,@RequestParam String reference,@RequestParam(required=false) MultipartFile proof) throws IOException {
        if(proof!=null && proof.getSize()>PaymentService.MAX_PROOF_BYTES)throw new com.restorant.exception.ApiException(400,"The proof image must be 3 MB or smaller");
        return payments.submit(user.getName(),id,reference,proof==null || proof.isEmpty()?null:proof.getBytes());
    }

    @GetMapping("/admin/payments") public List<Api.PaymentView> list(@RequestParam(required=false) Payment.Status status) { return payments.list(status); }
    @GetMapping("/admin/payments/{id}/proof") public ResponseEntity<byte[]> proof(@PathVariable Long id) {
        var proof=payments.proof(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(proof.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"payment-"+id+"-proof\"")
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(proof.data());
    }
    @PutMapping("/admin/payments/{id}/review")
    public Api.PaymentView review(Principal staff,@PathVariable Long id,@Valid @RequestBody Api.PaymentReview input) { return payments.review(id,input,staff.getName()); }

    @GetMapping("/admin/refunds") public List<Api.RefundView> refunds() { return payments.refunds(); }
    @GetMapping("/admin/refunds/due") public List<Api.BookingView> refundsDue() { return payments.refundsDue(); }
    @PostMapping("/admin/reservations/{id}/refunds") @ResponseStatus(HttpStatus.CREATED)
    public Api.RefundView refund(Principal staff,@PathVariable Long id,@Valid @RequestBody Api.RefundInput input) { return payments.createRefund(id,input,staff.getName()); }
    @PutMapping("/admin/refunds/{id}/complete")
    public Api.RefundView complete(Principal staff,@PathVariable Long id,@Valid @RequestBody Api.RefundCompletion input) { return payments.completeRefund(id,input,staff.getName()); }
    @PutMapping("/admin/refunds/{id}/void")
    public Api.RefundView voidRefund(Principal staff,@PathVariable Long id,@Valid @RequestBody Api.Note input) { return payments.voidRefund(id,input.note(),staff.getName()); }
    @GetMapping("/admin/reservations/{id}/audit") public List<Api.AuditView> audit(@PathVariable Long id) { return payments.history(id); }
}
