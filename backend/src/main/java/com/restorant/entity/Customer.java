package com.restorant.entity;
import jakarta.persistence.*;

// INHERITANCE: Customers reuse the identity validation shared by every account.
@Entity @DiscriminatorValue("CUSTOMER")
public class Customer extends User {
    protected Customer() {}
    public Customer(String name, String email, String phone, String hash) { super(name, email, phone, hash); }
    @Override public String getRole() { return "CUSTOMER"; }
    // POLYMORPHISM: The common User contract supplies permissions for the concrete account.
    @Override public boolean canManageReservations() { return false; }
}
