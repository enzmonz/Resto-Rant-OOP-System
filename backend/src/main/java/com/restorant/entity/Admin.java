package com.restorant.entity;
import jakarta.persistence.*;

// INHERITANCE: Admin shares identity rules without duplicating account data.
@Entity @DiscriminatorValue("ADMIN")
public class Admin extends User {
    protected Admin() {}
    public Admin(String name, String email, String phone, String hash) { super(name, email, phone, hash); }
    @Override public String getRole() { return "ADMIN"; }
    // POLYMORPHISM: Services can ask User for this capability without casting to Admin.
    @Override public boolean canManageReservations() { return true; }
}
