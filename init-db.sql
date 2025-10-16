-- PostgreSQL initialization script for Simple Trading Bot
-- This script runs automatically when the PostgreSQL container starts for the first time

-- Enable UUID extension for generating UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Grant privileges to tradingbot user
GRANT ALL PRIVILEGES ON DATABASE trading_bot TO tradingbot;

-- Create schema (optional, using public by default)
-- CREATE SCHEMA IF NOT EXISTS trading;
-- SET search_path TO trading, public;

-- Note: Tables (users, user_roles, trading_events, etc.) will be auto-created by Hibernate
-- using the spring.jpa.hibernate.ddl-auto=update setting in application.properties
