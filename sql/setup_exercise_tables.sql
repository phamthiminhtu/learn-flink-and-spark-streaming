-- Database Setup for Window Exercises
-- Run this in Postgres to create tables for exercise outputs

-- Connect to database:
-- docker exec -it postgres psql -U flink -d streaming

-- ============================================================
-- Module 2: Tumbling Windows
-- ============================================================

-- Exercise 2.1: Event counts per 5-minute window
CREATE TABLE IF NOT EXISTS event_counts_5min (
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    count BIGINT NOT NULL,
    PRIMARY KEY (window_start, event_type)
);

CREATE INDEX IF NOT EXISTS idx_event_counts_window ON event_counts_5min(window_start, window_end);

-- Exercise 2.2: Page view counts per 5-minute window
CREATE TABLE IF NOT EXISTS page_views_5min (
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    page_url VARCHAR(500) NOT NULL,
    count BIGINT NOT NULL,
    PRIMARY KEY (window_start, page_url)
);

CREATE INDEX IF NOT EXISTS idx_page_views_window ON page_views_5min(window_start, window_end);
CREATE INDEX IF NOT EXISTS idx_page_views_count ON page_views_5min(count DESC);

-- ============================================================
-- Module 4: Session Windows
-- ============================================================

-- Exercise 4.1: Session analytics (main deliverable)
CREATE TABLE IF NOT EXISTS user_sessions (
    session_id VARCHAR(200) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    duration_minutes DECIMAL(10,2),
    event_count INTEGER NOT NULL,
    pages_visited INTEGER NOT NULL,
    first_event_type VARCHAR(50),
    last_event_type VARCHAR(50),
    device_type VARCHAR(50),
    country VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_start ON user_sessions(start_time);
CREATE INDEX IF NOT EXISTS idx_sessions_duration ON user_sessions(duration_minutes DESC);

-- Exercise 4.2: Session metrics for dashboard
CREATE TABLE IF NOT EXISTS session_metrics_hourly (
    hour TIMESTAMP NOT NULL,
    device_type VARCHAR(50),
    country VARCHAR(50),
    total_sessions BIGINT NOT NULL,
    avg_duration_minutes DECIMAL(10,2),
    bounce_sessions BIGINT NOT NULL,
    bounce_rate DECIMAL(5,2),
    conversion_sessions BIGINT NOT NULL,
    conversion_rate DECIMAL(5,2),
    PRIMARY KEY (hour, device_type, country)
);

CREATE INDEX IF NOT EXISTS idx_session_metrics_hour ON session_metrics_hourly(hour);

-- Exercise 4.3: Abandoned carts
CREATE TABLE IF NOT EXISTS abandoned_carts (
    session_id VARCHAR(200) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    last_activity TIMESTAMP NOT NULL,
    items_added INTEGER NOT NULL,
    time_since_last_activity_minutes INTEGER,
    alert_sent BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_abandoned_carts_user ON abandoned_carts(user_id);
CREATE INDEX IF NOT EXISTS idx_abandoned_carts_activity ON abandoned_carts(last_activity);

-- ============================================================
-- Module 5: Late Data Handling
-- ============================================================

-- Exercise 5.2: Event counts with late data tracking
CREATE TABLE IF NOT EXISTS event_counts_with_late (
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    count BIGINT NOT NULL,
    is_late_update BOOLEAN DEFAULT FALSE,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (window_start, event_type)
);

CREATE INDEX IF NOT EXISTS idx_event_counts_late_updates ON event_counts_with_late(is_late_update);

-- Exercise 5.2: Late events tracking
CREATE TABLE IF NOT EXISTS late_events (
    id SERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_timestamp TIMESTAMP NOT NULL,
    processing_timestamp TIMESTAMP NOT NULL,
    lateness_ms BIGINT NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    reason VARCHAR(200) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_late_events_timestamp ON late_events(event_timestamp);
CREATE INDEX IF NOT EXISTS idx_late_events_lateness ON late_events(lateness_ms DESC);

-- ============================================================
-- Module 7: Real-World Patterns
-- ============================================================

-- Exercise 7.2: Funnel analysis
CREATE TABLE IF NOT EXISTS conversion_funnel (
    hour TIMESTAMP NOT NULL,
    stage VARCHAR(50) NOT NULL,
    users_entered BIGINT NOT NULL,
    users_converted BIGINT NOT NULL,
    conversion_rate DECIMAL(5,2),
    avg_time_to_next_seconds INTEGER,
    PRIMARY KEY (hour, stage)
);

CREATE INDEX IF NOT EXISTS idx_funnel_hour ON conversion_funnel(hour);

-- ============================================================
-- Utility Views for Analysis
-- ============================================================

-- View: Session summary statistics
CREATE OR REPLACE VIEW session_summary AS
SELECT
    DATE_TRUNC('hour', start_time) as hour,
    COUNT(*) as total_sessions,
    AVG(duration_minutes) as avg_duration,
    AVG(event_count) as avg_events_per_session,
    AVG(pages_visited) as avg_pages_per_session,
    COUNT(*) FILTER (WHERE event_count = 1) as bounce_sessions,
    ROUND(100.0 * COUNT(*) FILTER (WHERE event_count = 1) / COUNT(*), 2) as bounce_rate,
    COUNT(*) FILTER (WHERE last_event_type = 'purchase') as conversion_sessions,
    ROUND(100.0 * COUNT(*) FILTER (WHERE last_event_type = 'purchase') / COUNT(*), 2) as conversion_rate
FROM user_sessions
GROUP BY hour
ORDER BY hour DESC;

-- View: Top pages in most recent window
CREATE OR REPLACE VIEW top_pages_latest AS
SELECT
    window_start,
    window_end,
    page_url,
    count,
    RANK() OVER (PARTITION BY window_start ORDER BY count DESC) as rank
FROM page_views_5min
WHERE window_start = (SELECT MAX(window_start) FROM page_views_5min)
ORDER BY rank
LIMIT 10;

-- View: Late event statistics
CREATE OR REPLACE VIEW late_event_stats AS
SELECT
    DATE_TRUNC('hour', event_timestamp) as hour,
    COUNT(*) as late_event_count,
    AVG(lateness_ms / 1000.0) as avg_lateness_seconds,
    MAX(lateness_ms / 1000.0) as max_lateness_seconds,
    MIN(lateness_ms / 1000.0) as min_lateness_seconds,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY lateness_ms) / 1000.0 as median_lateness_seconds,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY lateness_ms) / 1000.0 as p95_lateness_seconds
FROM late_events
GROUP BY hour
ORDER BY hour DESC;

-- ============================================================
-- Example Queries for Exercises
-- ============================================================

-- Exercise 2.1 Verification: Event counts in latest window
-- SELECT * FROM event_counts_5min ORDER BY window_start DESC LIMIT 10;

-- Exercise 2.2 Verification: Top 10 pages in each window
-- SELECT * FROM page_views_5min WHERE window_start = (SELECT MAX(window_start) FROM page_views_5min) ORDER BY count DESC LIMIT 10;

-- Exercise 4.1 Verification: Session metrics
-- SELECT * FROM session_summary LIMIT 24;

-- Exercise 4.1 Analysis: Long sessions
-- SELECT user_id, session_id, duration_minutes, event_count, pages_visited
-- FROM user_sessions
-- WHERE duration_minutes > 30
-- ORDER BY duration_minutes DESC
-- LIMIT 20;

-- Exercise 4.1 Analysis: Users with multiple sessions
-- SELECT user_id, COUNT(*) as session_count, AVG(duration_minutes) as avg_duration
-- FROM user_sessions
-- GROUP BY user_id
-- HAVING COUNT(*) > 5
-- ORDER BY session_count DESC;

-- Exercise 4.3 Verification: Abandoned carts
-- SELECT * FROM abandoned_carts WHERE time_since_last_activity_minutes > 60 ORDER BY last_activity DESC;

-- Exercise 5.2 Verification: Late events by lateness
-- SELECT * FROM late_event_stats ORDER BY hour DESC LIMIT 24;

-- Exercise 5.2 Analysis: Which event types are often late?
-- SELECT event_type, COUNT(*) as late_count, AVG(lateness_ms / 1000.0) as avg_lateness_seconds
-- FROM late_events
-- GROUP BY event_type
-- ORDER BY late_count DESC;
