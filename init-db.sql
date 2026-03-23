-- PostgreSQL initialization script for Simple Trading Bot
-- This script runs automatically when the PostgreSQL container starts for the first time

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

GRANT ALL PRIVILEGES ON DATABASE trading_bot TO tradingbot;

-- Create agents table for Flyway migration compatibility
CREATE TABLE IF NOT EXISTS agents (
	id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
	name VARCHAR(255) NOT NULL,
	goal_type VARCHAR(64) NOT NULL,
	goal_description TEXT,
	trading_symbol VARCHAR(32) NOT NULL,
	capital NUMERIC(18,2) NOT NULL,
	status VARCHAR(32) NOT NULL,
	created_at TIMESTAMP NOT NULL DEFAULT NOW(),
	owner_id VARCHAR(255)
);
