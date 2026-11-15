-- Table package prices, per-table price snapshots, manual GCash payments, refunds and their audit trail.
-- Existing rows are kept; V5 (Java migration) backfills them into the new tables.

CREATE TABLE table_packages (
    capacity INT NOT NULL PRIMARY KEY,
    price DECIMAL(12,2) NOT NULL,
    updated_at TIMESTAMP(6) NULL,
    updated_by VARCHAR(254),
    CHECK (capacity BETWEEN 1 AND 100),
    CHECK (price >= 0)
);
INSERT INTO table_packages (capacity, price) VALUES (2, 600.00), (4, 1100.00), (6, 1600.00);
INSERT INTO table_packages (capacity, price)
    SELECT DISTINCT t.capacity, t.capacity * 250 + 100 FROM restaurant_tables t WHERE t.capacity NOT IN (2, 4, 6);

CREATE TABLE reservation_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    table_number VARCHAR(30) NOT NULL,
    capacity INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    refundable_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    cancelled_at TIMESTAMP(6) NULL,
    cancelled_by VARCHAR(254),
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(id),
    INDEX reservation_item_lookup (reservation_id, status)
);

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    method VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference VARCHAR(150) NOT NULL,
    receiving_account VARCHAR(30),
    has_proof BIT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP(6) NOT NULL,
    submitted_by VARCHAR(254) NOT NULL,
    reviewed_at TIMESTAMP(6) NULL,
    reviewed_by VARCHAR(254),
    review_note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX payment_reference_lookup (reference),
    INDEX payment_status_lookup (status, submitted_at)
);

CREATE TABLE payment_proofs (
    payment_id BIGINT NOT NULL PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL,
    data LONGBLOB NOT NULL,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    payment_reference VARCHAR(150) NOT NULL,
    refund_reference VARCHAR(150),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(254) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    completed_by VARCHAR(254),
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    FOREIGN KEY (payment_id) REFERENCES payments(id),
    CHECK (amount > 0),
    INDEX refund_reservation_lookup (reservation_id, status),
    INDEX refund_reference_lookup (refund_reference)
);

CREATE TABLE payment_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT,
    payment_id BIGINT,
    refund_id BIGINT,
    action VARCHAR(40) NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    actor VARCHAR(254) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    INDEX audit_reservation_lookup (reservation_id, id)
);

-- Lets a retried booking request return the reservation it already created.
ALTER TABLE reservations ADD COLUMN client_request_id VARCHAR(36);
CREATE UNIQUE INDEX reservation_client_request ON reservations (client_request_id);
