-- Run as a database administrator in MySQL Workbench or the MySQL CLI.
-- This creates only the database. Entity tables belong to Phase 2.
CREATE DATABASE IF NOT EXISTS resto_rant
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- Create the dedicated application account through Workbench (Users and Privileges).
-- Limit its host to localhost for local development and grant only
-- SELECT, INSERT, UPDATE, DELETE on resto_rant.*.
-- Use a separate schema-owner account for future schema migrations.
