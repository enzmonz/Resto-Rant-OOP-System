package com.restorant.entity;

import jakarta.persistence.*;

/** Optional screenshot a customer attaches to a payment. Kept apart so listing payments never loads image bytes. */
@Entity @Table(name = "payment_proofs")
public class PaymentProof {
    @Id private Long paymentId;
    @Column(nullable = false, length = 50) private String contentType;
    @Lob @Column(nullable = false) private byte[] data;
    protected PaymentProof() {}
    public PaymentProof(Long paymentId, String contentType, byte[] data) { this.paymentId = paymentId; this.contentType = contentType; this.data = data; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
