package com.streaming.flink;

import com.streaming.flink.model.ClickstreamEvent;
import com.streaming.flink.model.EnrichedClickstreamEvent;
import com.streaming.flink.serialization.ClickstreamEventDeserializationSchema;
import com.streaming.flink.serialization.EnrichedEventEncoder;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Flink DataStream job for processing clickstream events from Kafka.
 *
 * Key Flink Concepts Demonstrated:
 * 1. DataStream API - Core streaming abstraction
 * 2. Event Time Processing - Using event timestamps for windows
 * 3. Watermarks - Handling out-of-order events
 * 4. Time Windows - Tumbling windows for aggregation
 * 5. Kafka Source Connector - Reading from Kafka
 * 6. File Sink - Writing to S3/MinIO
 * 7. Checkpointing - Fault tolerance
 *
 * This job:
 * - Reads clickstream events from Kafka topic "clickstream"
 * - Assigns event time based on event timestamp field
 * - Creates 5-minute tumbling windows
 * - Enriches events with window start/end times
 * - Writes to S3/MinIO in JSONL format
 */
public class ClickstreamProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ClickstreamProcessor.class);

    // Configuration constants
    // Supports both local (localhost) and Docker (kafka) hostnames
    private static final String KAFKA_BOOTSTRAP_SERVERS =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    private static final String KAFKA_TOPIC = "clickstream";
    // Use Flink's native S3 filesystem (s3://, not s3a://)
    // Configuration comes from FLINK_PROPERTIES in docker-compose.yml
    private static final String OUTPUT_PATH = System.getenv().getOrDefault("OUTPUT_PATH", "s3://lakehouse/clickstream-events-flink/");
    private static final String CHECKPOINT_PATH = System.getenv().getOrDefault("CHECKPOINT_PATH", "s3://checkpoints/clickstream-events-flink/");

    public static void main(String[] args) throws Exception {

        // 1. Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Configure parallelism (can be overridden at runtime)
        env.setParallelism(1);

        // 2. Enable checkpointing for fault tolerance
        // FileSink REQUIRES checkpointing to be enabled to work properly
        env.enableCheckpointing(10000, CheckpointingMode.AT_LEAST_ONCE); // 10 seconds
        env.getCheckpointConfig().setCheckpointStorage(CHECKPOINT_PATH);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
        env.getCheckpointConfig().setCheckpointTimeout(60000);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        // 3. Configure restart strategy
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
            3,  // Number of restart attempts
            org.apache.flink.api.common.time.Time.seconds(10)  // Delay between restarts
        ));

        // S3/MinIO configuration comes from FLINK_PROPERTIES in docker-compose.yml
        // No need to configure here when running in Docker cluster
        // configureS3Access(env);

        LOG.info("📖 Starting Flink Clickstream Processor");
        LOG.info("   📥 Kafka source: {}/{}", KAFKA_BOOTSTRAP_SERVERS, KAFKA_TOPIC);
        LOG.info("   💾 Output path: {}", OUTPUT_PATH);
        LOG.info("   🔖 Checkpoint path: {}", CHECKPOINT_PATH);
        LOG.info("   📊 Window size: 5 minutes");
        LOG.info("   ⏰ Max out-of-orderness: 10 minutes");

        // 4. Create Kafka source
        // Using the KafkaSource API (Flink 1.14+)
        KafkaSource<ClickstreamEvent> source = KafkaSource.<ClickstreamEvent>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(KAFKA_TOPIC)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new ClickstreamEventDeserializationSchema())
            .setProperty("group.id", "flink-clickstream-processor")
            // Kafka consumer properties
            .setProperty("enable.auto.commit", "false")
            .setProperty("auto.offset.reset", "earliest")
            // Connection retry settings (helps with Docker/localhost connection issues)
            .setProperty("reconnect.backoff.ms", "1000")
            .setProperty("reconnect.backoff.max.ms", "10000")
            .setProperty("request.timeout.ms", "60000")
            .setProperty("session.timeout.ms", "30000")
            .setProperty("metadata.max.age.ms", "30000")
            .build();

        // 5. Create DataStream from Kafka source with Watermark Strategy
        // This is a KEY FLINK CONCEPT: Event Time + Watermarks
        DataStream<ClickstreamEvent> events = env.fromSource(
            source,
            WatermarkStrategy
                // Extract timestamp from event (timestamp field is in milliseconds)
                .<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofMinutes(10))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp()),
            "Kafka Clickstream Source"
        );

        // Filter out null events (from deserialization errors)
        events = events.filter(event -> event != null);

        // 6. Simple map transformation without windowing (temporarily removed to debug)
        // Convert ClickstreamEvent to EnrichedClickstreamEvent with current timestamp
        DataStream<EnrichedClickstreamEvent> enrichedEvents = events
            .map(event -> {
                long currentTime = System.currentTimeMillis();
                return EnrichedClickstreamEvent.fromEvent(event, currentTime, currentTime + 60000);
            });

        // 7. Create PostgreSQL JDBC Sink
        // This is much more reliable than FileSink and avoids stream closure issues
        String jdbcUrl = "jdbc:postgresql://postgres:5432/streaming";
        String username = "flink";
        String password = "flink123";

        String insertSQL = "INSERT INTO clickstream_events " +
            "(event_id, user_id, session_id, event_type, page_url, " +
            "ip_address, user_agent, referrer, country, device_type, timestamp, " +
            "event_timestamp, event_timestamp_start, event_timestamp_end) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (event_id, timestamp) DO UPDATE SET " +
            "user_id = EXCLUDED.user_id, " +
            "event_type = EXCLUDED.event_type";

        enrichedEvents.addSink(
            JdbcSink.sink(
                insertSQL,
                (JdbcStatementBuilder<EnrichedClickstreamEvent>) (statement, event) -> {
                    statement.setString(1, event.getEventId());
                    statement.setString(2, event.getUserId());
                    statement.setString(3, event.getSessionId());
                    statement.setString(4, event.getEventType());
                    statement.setString(5, event.getPageUrl());
                    statement.setString(6, event.getIpAddress());
                    statement.setString(7, event.getUserAgent());
                    statement.setString(8, event.getReferrer());
                    statement.setString(9, event.getCountry());
                    statement.setString(10, event.getDeviceType());
                    statement.setLong(11, event.getTimestamp());

                    // Convert ISO timestamp strings to SQL Timestamp
                    statement.setTimestamp(12, event.getEventTimestamp() != null ?
                        Timestamp.from(Instant.parse(event.getEventTimestamp())) : null);
                    statement.setTimestamp(13, event.getEventTimestampStart() != null ?
                        Timestamp.from(Instant.parse(event.getEventTimestampStart())) : null);
                    statement.setTimestamp(14, event.getEventTimestampEnd() != null ?
                        Timestamp.from(Instant.parse(event.getEventTimestampEnd())) : null);
                },
                JdbcExecutionOptions.builder()
                    .withBatchSize(100)  // Batch 100 records before writing
                    .withBatchIntervalMs(1000)  // Write every 1 second
                    .withMaxRetries(3)
                    .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                    .withUrl(jdbcUrl)
                    .withDriverName("org.postgresql.Driver")
                    .withUsername(username)
                    .withPassword(password)
                    .build()
            )
        );

        LOG.info("📊 JDBC Sink configured: {}", jdbcUrl);

        // 9. Execute the job
        // The job will run until manually cancelled or fails
        env.execute("Flink Clickstream Processor");
    }

    /**
     * Configure S3/MinIO access credentials and endpoint.
     * This matches the Spark S3A configuration.
     * Uses environment variables to support both local and Docker deployments.
     */
    private static void configureS3Access(StreamExecutionEnvironment env) {
        org.apache.flink.configuration.Configuration config = new org.apache.flink.configuration.Configuration();

        // S3 endpoint (MinIO) - use env var for Docker compatibility
        String s3Endpoint = System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000");
        config.setString("s3.endpoint", s3Endpoint);
        config.setString("s3.access-key", "admin");
        config.setString("s3.secret-key", "password123");
        config.setString("s3.path.style.access", "true");

        // Use env configuration
        env.getConfig().setGlobalJobParameters(config);

        LOG.info("✅ S3/MinIO configuration applied");
    }
}
