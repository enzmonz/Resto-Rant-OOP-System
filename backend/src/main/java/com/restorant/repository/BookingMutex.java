package com.restorant.repository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class BookingMutex {
    private final EntityManager em;
    public BookingMutex(EntityManager em) { this.em = em; }
    // ABSTRACTION: Services request an allocation lock without knowing the database locking syntax.
    // The seeded singleton row serializes booking/table mutations across all application instances.
    public void acquire() { em.createNativeQuery("SELECT id FROM booking_lock WHERE id = 1 FOR UPDATE").getSingleResult(); }
}
