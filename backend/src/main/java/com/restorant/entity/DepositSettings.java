package com.restorant.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="deposit_policy")
public class DepositSettings {
    @Id private Long id;
    @Column(precision=12,scale=2) private BigDecimal amount;
    @Column(length=1000) private String method;
    @Column(nullable=false) private boolean perPerson;
    public BigDecimal getAmount(){return amount;}public String getMethod(){return method;}public boolean isPerPerson(){return perPerson;}
    public void configure(BigDecimal amount,String method,boolean perPerson){
        if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.compareTo(new BigDecimal("1000000"))>0 || method==null || method.isBlank() || method.length()>1000)throw new IllegalArgumentException("Enter a positive deposit (up to 1,000,000) and payment instructions");
        this.amount=amount;this.method=method.trim();this.perPerson=perPerson;
    }
}
