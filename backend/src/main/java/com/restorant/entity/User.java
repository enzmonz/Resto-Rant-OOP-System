package com.restorant.entity;

import jakarta.persistence.*;
import java.util.Locale;

// ABSTRACTION: Common identity rules belong here; each subtype supplies its role.
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role")
public abstract class User {
    // ENCAPSULATION: Identity and credentials cannot be changed through arbitrary setters.
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String fullName;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 30)
    private String phoneNumber;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false) private boolean enabled = true;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    protected User() {}
    protected User(String name, String email, String phone, String passwordHash) {
        updateContact(name, phone);
        if (email == null || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.trim().length() > 254)
            throw new IllegalArgumentException("A valid email is required");
        this.email = email.trim().toLowerCase(Locale.ROOT);
        if (passwordHash == null || !passwordHash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("A BCrypt password hash is required");
        this.passwordHash = passwordHash;
    }
    public void updateContact(String name, String phone) {
        if (name == null || name.isBlank() || name.trim().length() > 120)
            throw new IllegalArgumentException("Name must contain 1–120 characters");
        if (phone == null || !phone.matches("[+0-9() .-]{7,30}"))
            throw new IllegalArgumentException("A valid phone number is required");
        fullName = name.trim();
        phoneNumber = phone;
    }
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getPasswordHash() { return passwordHash; }
    public abstract String getRole();
    public abstract boolean canManageReservations();
}
