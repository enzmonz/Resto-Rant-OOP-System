CREATE TABLE reservation_tables (
    reservation_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    PRIMARY KEY (reservation_id, table_id),
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(id)
);
INSERT INTO reservation_tables (reservation_id, table_id) SELECT id, table_id FROM reservations;
ALTER TABLE reservations ADD COLUMN guest_needs VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE reservations ADD COLUMN deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE reservations ADD COLUMN deposit_status VARCHAR(25) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE reservations ADD COLUMN payment_reference VARCHAR(150);
ALTER TABLE reservations ADD COLUMN payment_note VARCHAR(500);
ALTER TABLE users ADD COLUMN enabled BIT NOT NULL DEFAULT 1;
CREATE TABLE inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    message VARCHAR(3000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    resolved BIT NOT NULL DEFAULT 0
);
