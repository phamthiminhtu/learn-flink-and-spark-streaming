package com.streaming.flink.exercises;

import com.streaming.flink.model.ClickstreamEvent;
import com.streaming.flink.serialization.ClickstreamEventDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Exercise 4.1: Session Analytics with 30-minute Gap
 *
 * This job demonstrates session window implementation in Flink:
 * - Session gap: 30 minutes
 * - Tracks per-session metrics: duration, event count, pages visited
 * - Outputs to user_sessions table
 *
 * Key Concepts:
 * - EventTimeSessionWindows: Dynamic windows based on inactivity gap
 * - ProcessWindowFunction: Access to window metadata and all events
 * - Session merging: When events within gap arrive
 */
public class SessionAnalyticsProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SessionAnalyticsProcessor.class);

    private static final String KAFKA_BOOTSTRAP_SERVERS =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092");
    private static final String KAFKA_TOPIC = "clickstream";
    private static final int SESSION_GAP_MINUTES = 30;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        LOG.info("Starting Session Analytics Processor");
        LOG.info("Session gap: {} minutes", SESSION_GAP_MINUTES);

        // Create Kafka source
        KafkaSource<ClickstreamEvent> source = KafkaSource.<ClickstreamEvent>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(KAFKA_TOPIC)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new ClickstreamEventDeserializationSchema())
            .setProperty("group.id", "session-analytics-processor")
            .build();

        // Read from Kafka with watermark strategy
        DataStream<ClickstreamEvent> events = env.fromSource(
            source,
            WatermarkStrategy
                .<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofMinutes(10))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp()),
            "Kafka Clickstream Source"
        ).filter(event -> event != null);

        // Apply session windows and process
        DataStream<SessionMetrics> sessionMetrics = events
            .keyBy(ClickstreamEvent::getUserId)
            .window(EventTimeSessionWindows.withGap(Time.minutes(SESSION_GAP_MINUTES)))
            .process(new SessionAnalyticsFunction());

        // Sink to Postgres
        String jdbcUrl = "jdbc:postgresql://postgres:5432/streaming";
        String insertSQL =
            "INSERT INTO user_sessions " +
            "(session_id, user_id, start_time, end_time, duration_minutes, " +
            "event_count, pages_visited, first_event_type, last_event_type, " +
            "device_type, country) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (session_id) DO UPDATE SET " +
            "end_time = EXCLUDED.end_time, " +
            "duration_minutes = EXCLUDED.duration_minutes, " +
            "event_count = EXCLUDED.event_count, " +
            "pages_visited = EXCLUDED.pages_visited, " +
            "last_event_type = EXCLUDED.last_event_type";

        sessionMetrics.addSink(
            JdbcSink.sink(
                insertSQL,
                (JdbcStatementBuilder<SessionMetrics>) (statement, metrics) -> {
                    statement.setString(1, metrics.sessionId);
                    statement.setString(2, metrics.userId);
                    statement.setTimestamp(3, Timestamp.from(Instant.ofEpochMilli(metrics.startTime)));
                    statement.setTimestamp(4, Timestamp.from(Instant.ofEpochMilli(metrics.endTime)));
                    statement.setDouble(5, metrics.durationMinutes);
                    statement.setInt(6, metrics.eventCount);
                    statement.setInt(7, metrics.pagesVisited);
                    statement.setString(8, metrics.firstEventType);
                    statement.setString(9, metrics.lastEventType);
                    statement.setString(10, metrics.deviceType);
                    statement.setString(11, metrics.country);
                },
                JdbcExecutionOptions.builder()
                    .withBatchSize(50)
                    .withBatchIntervalMs(1000)
                    .withMaxRetries(3)
                    .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                    .withUrl(jdbcUrl)
                    .withDriverName("org.postgresql.Driver")
                    .withUsername("flink")
                    .withPassword("flink123")
                    .build()
            )
        );

        LOG.info("Session metrics will be written to user_sessions table");
        env.execute("Session Analytics Processor");
    }

    /**
     * Session Metrics POJO
     */
    public static class SessionMetrics {
        public String sessionId;
        public String userId;
        public long startTime;
        public long endTime;
        public double durationMinutes;
        public int eventCount;
        public int pagesVisited;
        public String firstEventType;
        public String lastEventType;
        public String deviceType;
        public String country;

        public SessionMetrics() {}

        public SessionMetrics(String sessionId, String userId, long startTime, long endTime,
                            int eventCount, int pagesVisited, String firstEventType,
                            String lastEventType, String deviceType, String country) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = (endTime - startTime) / 60000.0; // Convert ms to minutes
            this.eventCount = eventCount;
            this.pagesVisited = pagesVisited;
            this.firstEventType = firstEventType;
            this.lastEventType = lastEventType;
            this.deviceType = deviceType;
            this.country = country;
        }
    }

    /**
     * ProcessWindowFunction for session analytics
     *
     * Receives all events in the session window and computes metrics.
     * Note: For production with large sessions, consider using AggregateFunction
     * to incrementally compute metrics instead of buffering all events.
     */
    public static class SessionAnalyticsFunction
            extends ProcessWindowFunction<ClickstreamEvent, SessionMetrics, String, TimeWindow> {

        @Override
        public void process(String userId, Context context,
                          Iterable<ClickstreamEvent> events,
                          Collector<SessionMetrics> out) {

            TimeWindow window = context.window();

            // Generate unique session ID
            String sessionId = userId + "-" + window.getStart();

            // Collect metrics
            int eventCount = 0;
            Set<String> uniquePages = new HashSet<>();
            String firstEventType = null;
            String lastEventType = null;
            String deviceType = null;
            String country = null;
            long minTimestamp = Long.MAX_VALUE;
            long maxTimestamp = Long.MIN_VALUE;

            for (ClickstreamEvent event : events) {
                eventCount++;

                // Track first and last events
                if (event.getTimestamp() < minTimestamp) {
                    minTimestamp = event.getTimestamp();
                    firstEventType = event.getEventType();
                }
                if (event.getTimestamp() > maxTimestamp) {
                    maxTimestamp = event.getTimestamp();
                    lastEventType = event.getEventType();
                }

                // Track unique pages
                if (event.getPageUrl() != null) {
                    uniquePages.add(event.getPageUrl());
                }

                // Capture device and country (assume consistent within session)
                if (deviceType == null) {
                    deviceType = event.getDeviceType();
                }
                if (country == null) {
                    country = event.getCountry();
                }
            }

            // Create and emit session metrics
            SessionMetrics metrics = new SessionMetrics(
                sessionId,
                userId,
                window.getStart(),
                window.getEnd(),
                eventCount,
                uniquePages.size(),
                firstEventType,
                lastEventType,
                deviceType,
                country
            );

            out.collect(metrics);

            LOG.debug("Session completed: user={}, duration={}min, events={}",
                userId, metrics.durationMinutes, eventCount);
        }
    }
}
