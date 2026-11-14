CREATE TABLE deposit_policy (
    id BIGINT PRIMARY KEY,
    amount DECIMAL(12,2),
    method VARCHAR(1000),
    per_person BIT NOT NULL DEFAULT 0
);
INSERT INTO deposit_policy (id, amount, method, per_person) VALUES (1, NULL, NULL, 0);
