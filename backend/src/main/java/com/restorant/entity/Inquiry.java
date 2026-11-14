package com.restorant.entity;
import jakarta.persistence.*;
import java.time.Instant;
import com.restorant.dto.Api;

@Entity @Table(name="inquiries")
public class Inquiry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=120) private String fullName;
    @Column(nullable=false,length=254) private String email;
    @Column(nullable=false,length=30) private String phoneNumber;
    @Column(nullable=false,length=3000) private String message;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private boolean resolved;
    protected Inquiry() {}
    public Inquiry(Api.InquiryInput input,Instant now){fullName=input.fullName().trim();email=input.email().trim();phoneNumber=input.phoneNumber();message=input.message().trim();createdAt=now;}
    public void resolve(){resolved=true;}
    public Long getId(){return id;} public String getFullName(){return fullName;} public String getEmail(){return email;}
    public String getPhoneNumber(){return phoneNumber;} public String getMessage(){return message;}
    public Instant getCreatedAt(){return createdAt;} public boolean isResolved(){return resolved;}
}
