# Start Here: Window Processing Learning Path

Welcome! This document will guide you through learning stream processing windows with a structured approach.

## Your Learning Goals

You want to master:
- **Tumbling, Sliding, Session Windows**: Implementation patterns
- **Watermark strategies**: Late data, allowed lateness
- **Spark**: GroupBy + window(), watermark()
- **Flink**: WindowAssigner, Trigger, Evictor
- **Practice**: Sessionization logic (30-min timeout)
- **Deliverable**: Session analytics showing gap-based windowing

## Step-by-Step Learning Path

### Phase 1: Foundation (1-2 hours)

**Start with visualization:**
1. Read [Visual Guide](windowing-visual-guide.md) to understand concepts through diagrams
   - Window types comparison
   - Event time vs processing time
   - Watermark mechanics
   - Late data handling

**Theory:**
2. Read Module 1 & 2 in [Syllabus](windowing-syllabus.md)
   - Time concepts
   - Tumbling windows

### Phase 2: Basic Windows (2-3 hours)

**Hands-on Practice:**
3. Run Exercise 2.1: Tumbling Window Event Counts
   ```bash
   # Setup database tables (run from project root directory)
   docker exec -i postgres psql -U flink -d streaming < sql/setup_exercise_tables.sql

   # Run Spark version
   ./spark/docker-run.sh submit solution/tumbling_window_exercise.py

   # Or Flink version
   # (compile and submit Java code)
   ```

4. Verify results:
   ```sql
   docker exec -it postgres psql -U flink -d streaming
   SELECT * FROM event_counts_5min ORDER BY window_start DESC LIMIT 10;
   ```

5. Read Module 3 in [Syllabus](windowing-syllabus.md) - Sliding windows

6. Run Exercise 3.1: Sliding Window Moving Average
   ```bash
   ./spark/docker-run.sh submit solution/sliding_window_exercise.py
   ```

### Phase 3: Session Windows (3-4 hours) - YOUR MAIN DELIVERABLE

**Core Learning:**
7. Read Module 4 in [Syllabus](windowing-syllabus.md) - Session windows
   - Understand gap-based windowing
   - Session merging
   - Dynamic window sizing

**Implementation:**
8. Study the session analytics code:
   - Flink: `flink/src/main/java/.../exercises/SessionAnalyticsProcessor.java`
   - Spark: `spark/src/solution/session_analytics.py`

9. Run Exercise 4.1: Session Analytics (30-min gap)
   ```bash
   # Spark version (easier to run)
   ./spark/docker-run.sh submit solution/session_analytics.py

   # Or Flink version (requires compiling Java)
   # Add SessionAnalyticsProcessor.java to flink/src, then:
   cd flink
   mvn clean package
   docker exec flink-jobmanager flink run /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar
   ```

10. Analyze session results:
    ```sql
    -- View sessions
    SELECT * FROM user_sessions ORDER BY start_time DESC LIMIT 10;

    -- Average session duration by device
    SELECT device_type,
           COUNT(*) as sessions,
           ROUND(AVG(duration_minutes), 2) as avg_duration,
           ROUND(AVG(pages_visited), 1) as avg_pages
    FROM user_sessions
    GROUP BY device_type;

    -- Bounce rate
    SELECT
      COUNT(*) as total_sessions,
      COUNT(*) FILTER (WHERE event_count = 1) as bounces,
      ROUND(100.0 * COUNT(*) FILTER (WHERE event_count = 1) / COUNT(*), 2) as bounce_rate
    FROM user_sessions;
    ```

### Phase 4: Advanced Concepts (2-3 hours)

**Watermarks & Late Data:**
11. Read Module 5 in [Syllabus](windowing-syllabus.md) - Watermarks

12. Run Exercise 5.2: Late Data Handling
    ```bash
    # Flink implementation (better late data support)
    docker exec -it flink-jobmanager flink run /path/to/LateDataHandlingExercise.jar
    ```

13. Analyze late events:
    ```sql
    -- Late event distribution
    SELECT * FROM late_events ORDER BY lateness_ms DESC LIMIT 20;

    -- Which event types are often late?
    SELECT event_type,
           COUNT(*) as late_count,
           AVG(lateness_ms / 1000.0) as avg_lateness_seconds
    FROM late_events
    GROUP BY event_type
    ORDER BY late_count DESC;
    ```

### Phase 5: Real-World Application (Optional, 3-4 hours)

14. Read Module 7 in [Syllabus](windowing-syllabus.md) - Real-world patterns
15. Try Exercise 7.2: Real-time Funnel Analysis
16. Build your own use case using the patterns learned

## Quick Reference While Coding

Keep these open as you code:
- [Quick Reference Guide](windowing-quick-reference.md) - Code snippets
- [Visual Guide](windowing-visual-guide.md) - Concept diagrams

## Key Files for Your Deliverable

Your session analytics deliverable (30-min gap) includes:

**Flink Implementation:**
```
exercises/flink/solution/SessionAnalyticsProcessor.java
```
Key parts:
- `EventTimeSessionWindows.withGap(Time.minutes(30))` (line 58)
- `SessionAnalyticsFunction` (computes metrics per session)
- Output to `user_sessions` table

**Spark Implementation:**
```
exercises/spark/solution/session_analytics.py
```
Key parts:
- Custom session logic using window functions
- 30-minute gap detection (1800 seconds)
- Session ID assignment
- Output to Delta Lake

**Database Schema:**
```sql
CREATE TABLE user_sessions (
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
```

## Expected Deliverable Outputs

After running the session analytics:

**1. Session Metrics:**
```sql
SELECT
    session_id,
    user_id,
    duration_minutes,
    event_count,
    pages_visited,
    first_event_type,
    last_event_type
FROM user_sessions
LIMIT 10;

-- Example output:
-- user123-1698765432000 | user123 | 25.5 | 15 | 8 | page_view | purchase
```

**2. Session Summary:**
```sql
SELECT * FROM session_summary LIMIT 24;

-- Shows:
-- - Sessions per hour
-- - Average duration
-- - Bounce rate
-- - Conversion rate
```

**3. Insights:**
- Average session duration: ~X minutes
- Bounce rate: Y%
- Conversion rate: Z%
- Peak hours: when?
- Device comparison: mobile vs desktop

## Troubleshooting

### No sessions appearing?
- Check if events are being generated: `SELECT COUNT(*) FROM clickstream_events;`
- Verify watermark is advancing (Flink UI)
- Ensure 30+ minutes of data has been generated

### Sessions not closing?
- Session closes after 30-minute gap + watermark delay
- Need to wait for timeout period
- Generate more events to advance watermark

### Flink job fails?
- Check Flink logs: `docker logs flink-jobmanager`
- Verify database connectivity
- Ensure all dependencies in pom.xml

### Spark job slow?
- Check Spark UI (http://localhost:4040)
- Verify watermark configuration
- Consider using append mode if possible

## Assessment Checklist

You've mastered windowing when you can:

- [ ] Explain the difference between tumbling, sliding, and session windows
- [ ] Implement a 30-minute gap-based session window
- [ ] Configure watermark strategies appropriately
- [ ] Handle late data with allowed lateness
- [ ] Calculate session metrics (duration, event count, pages visited)
- [ ] Analyze bounce rate and conversion rate from sessions
- [ ] Optimize state size for large-scale processing
- [ ] Debug watermark lag issues
- [ ] Choose the right window type for a use case

## Next Steps After Completion

1. **Customize**: Adapt session analytics to your specific use case
2. **Optimize**: Implement incremental aggregation for large sessions
3. **Scale**: Test with high-volume data
4. **Extend**: Add real-time alerting for abandoned carts
5. **Explore**: Try the final project in Module 9 of the syllabus

## Resources Quick Links

- **Main Syllabus**: [windowing-syllabus.md](windowing-syllabus.md)
- **Visual Guide**: [windowing-visual-guide.md](windowing-visual-guide.md)
- **Quick Reference**: [windowing-quick-reference.md](windowing-quick-reference.md)
- **Exercise Instructions**: [../exercises/README.md](../exercises/README.md)
- **Database Setup**: [../exercises/sql/setup_exercise_tables.sql](../exercises/sql/setup_exercise_tables.sql)

## Getting Help

1. Check inline comments in solution code
2. Review visual diagrams for concept clarity
3. Consult quick reference for syntax
4. Read syllabus for detailed explanations
5. Check Flink/Spark documentation

## Time Estimate

- **Quick Path** (focus on deliverable only): 4-6 hours
  - Phase 1: 1 hour
  - Phase 3: 3-4 hours
  - Testing: 1 hour

- **Complete Path** (all exercises): 12-15 hours
  - All phases + experimentation

Start with the Visual Guide, then dive into the Session Analytics exercise. Good luck!
