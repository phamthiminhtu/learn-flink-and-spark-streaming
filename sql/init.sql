-- PostgreSQL initialization script for Flink streaming clickstream data
-- This script creates the table schema for storing enriched clickstream events

-- Create the clickstream_events table
CREATE TABLE IF NOT EXISTS clickstream_events (
    id SERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    page_url VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent TEXT,
    referrer VARCHAR(500),
    country VARCHAR(100),
    device_type VARCHAR(50),
    timestamp BIGINT NOT NULL,
    event_timestamp TIMESTAMP NOT NULL,
    event_timestamp_start TIMESTAMP,
    event_timestamp_end TIMESTAMP,
    processing_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_event UNIQUE (event_id, timestamp)
);

-- Create indexes for better query performance
CREATE INDEX idx_user_id ON clickstream_events(user_id);
CREATE INDEX idx_event_type ON clickstream_events(event_type);
CREATE INDEX idx_event_timestamp ON clickstream_events(event_timestamp);
CREATE INDEX idx_session_id ON clickstream_events(session_id);

-- Display table info
\d+ clickstream_events;

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE clickstream_events TO flink;
GRANT USAGE, SELECT ON SEQUENCE clickstream_events_id_seq TO flink;
