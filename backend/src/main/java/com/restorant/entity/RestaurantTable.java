package com.restorant.entity;
import jakarta.persistence.*;

@Entity @Table(name = "restaurant_tables")
public class RestaurantTable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String tableNumber;
    @Column(nullable = false)
    private int capacity;
    @Column(nullable = false)
    private boolean enabled = true;
    @Version private long version;
    protected RestaurantTable() {}
    public RestaurantTable(String number, int capacity) { update(number, capacity); }
    // ENCAPSULATION: Reject invalid changes before modifying either attribute.
    public void update(String number, int capacity) {
        if (number == null || number.isBlank() || number.trim().length() > 30 || capacity < 1 || capacity > 100)
            throw new IllegalArgumentException("Table number and capacity (1–100) are required");
        tableNumber = number.trim();
        this.capacity = capacity;
    }
    public boolean accommodates(int guests) { return enabled && guests > 0 && guests <= capacity; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getId() { return id; }
    public String getTableNumber() { return tableNumber; }
    public int getCapacity() { return capacity; }
    public boolean isEnabled() { return enabled; }
}
