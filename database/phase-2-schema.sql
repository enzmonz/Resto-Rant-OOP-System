USE resto_rant;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(31) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    phone_number VARCHAR(30) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE restaurant_tables (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    table_number VARCHAR(30) NOT NULL UNIQUE,
    capacity INT NOT NULL,
    enabled BIT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (capacity BETWEEN 1 AND 100)
);

CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference_number VARCHAR(36) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    guest_count INT NOT NULL,
    special_requests VARCHAR(1000),
    status ENUM('PENDING','CONFIRMED','CANCELLED','COMPLETED') NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (customer_id) REFERENCES users(id),
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(id),
    CHECK (guest_count BETWEEN 1 AND 100),
    CHECK (ends_at > starts_at),
    INDEX reservation_availability (table_id, starts_at, ends_at, status),
    INDEX customer_history (customer_id, starts_at)
);
