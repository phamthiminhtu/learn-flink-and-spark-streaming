package com.streaming.flink.exercises;

import com.streaming.flink.model.ClickstreamEvent;
import com.streaming.flink.serialization.ClickstreamEventDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Exercise 5.2: Comprehensive Late Data Handling
 *
 * Demonstrates:
 * 1. Watermark configuration (10-minute maxOutOfOrderness)
 * 2. Allowed lateness (5 minutes after watermark)
 * 3. Side output for too-late events
 * 4. Tracking and logging late events
 *
 * Event Categories:
 * - On-time: Arrives before watermark passes window end
 * - Late (allowed): Arrives after watermark but within allowed lateness
 * - Too late: Arrives after allowed lateness → goes to side output
 *
 * Key Concepts:
 * - Watermark = max_event_time - maxOutOfOrderness
 * - Window closes when watermark >= window.end + allowedLateness
 * - Late events can trigger window re-computation
 */
public class LateDataHandlingExercise {

    private static final Logger LOG = LoggerFactory.getLogger(LateDataHandlingExercise.class);

    private static final String KAFKA_BOOTSTRAP_SERVERS =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092");
    private static final String KAFKA_TOPIC = "clickstream";

    // Side output tag for late events
    private static final OutputTag<LateEvent> lateEventsTag =
        new OutputTag<LateEvent>("late-events") {};

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        LOG.info("Starting Late Data Handling Exercise");
        LOG.info("Watermark: 10-minute bounded out-of-orderness");
        LOG.info("Allowed lateness: 5 minutes");
        LOG.info("Total tolerance: 15 minutes");

        // Create Kafka source
        KafkaSource<ClickstreamEvent> source = KafkaSource.<ClickstreamEvent>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(KAFKA_TOPIC)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new ClickstreamEventDeserializationSchema())
            .setProperty("group.id", "late-data-exercise")
            .build();

        // Watermark strategy: 10-minute tolerance
        DataStream<ClickstreamEvent> events = env.fromSource(
            source,
            WatermarkStrategy
                .<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofMinutes(10))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp()),
            "Kafka Source"
        ).filter(event -> event != null);

        // Apply windowing with allowed lateness
        SingleOutputStreamOperator<EventCount> windowCounts = events
            .keyBy(ClickstreamEvent::getEventType)
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .allowedLateness(Time.minutes(5))  // Accept late data up to 5 min after watermark
            .sideOutputLateData(lateEventsTag)  // Capture too-late events
            .aggregate(
                new CountAggregator(),
                new AddWindowMetadataWithLateness()
            );

        // Main stream: on-time and late-but-allowed events
        String jdbcUrl = "jdbc:postgresql://postgres:5432/streaming";
        String insertSQL =
            "INSERT INTO event_counts_with_late " +
            "(window_start, window_end, event_type, count, is_late_update) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT (window_start, event_type) DO UPDATE SET " +
            "count = EXCLUDED.count, " +
            "is_late_update = EXCLUDED.is_late_update";

        windowCounts.addSink(
            JdbcSink.sink(
                insertSQL,
                (JdbcStatementBuilder<EventCount>) (statement, count) -> {
                    statement.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(count.windowStart)));
                    statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(count.windowEnd)));
                    statement.setString(3, count.eventType);
                    statement.setLong(4, count.count);
                    statement.setBoolean(5, count.isLateUpdate);
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

        // Side output: too-late events
        DataStream<LateEvent> lateEvents = windowCounts.getSideOutput(lateEventsTag);

        String lateEventSQL =
            "INSERT INTO late_events " +
            "(event_id, event_type, event_timestamp, processing_timestamp, " +
            "lateness_ms, window_start, window_end, reason) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        lateEvents.addSink(
            JdbcSink.sink(
                lateEventSQL,
                (JdbcStatementBuilder<LateEvent>) (statement, late) -> {
                    statement.setString(1, late.eventId);
                    statement.setString(2, late.eventType);
                    statement.setTimestamp(3, Timestamp.from(Instant.ofEpochMilli(late.eventTimestamp)));
                    statement.setTimestamp(4, Timestamp.from(Instant.ofEpochMilli(late.processingTimestamp)));
                    statement.setLong(5, late.latenessMs);
                    statement.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(late.windowStart)));
                    statement.setTimestamp(7, Timestamp.from(Instant.ofEpochMilli(late.windowEnd)));
                    statement.setString(8, late.reason);
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

        LOG.info("Results:");
        LOG.info("  Main counts → event_counts_with_late");
        LOG.info("  Late events → late_events");

        env.execute("Late Data Handling Exercise");
    }

    // ===================== POJOs =====================

    public static class EventCount {
        public long windowStart;
        public long windowEnd;
        public String eventType;
        public long count;
        public boolean isLateUpdate;

        public EventCount() {}

        public EventCount(long windowStart, long windowEnd, String eventType,
                         long count, boolean isLateUpdate) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.eventType = eventType;
            this.count = count;
            this.isLateUpdate = isLateUpdate;
        }
    }

    public static class LateEvent {
        public String eventId;
        public String eventType;
        public long eventTimestamp;
        public long processingTimestamp;
        public long latenessMs;
        public long windowStart;
        public long windowEnd;
        public String reason;

        public LateEvent() {}

        public LateEvent(String eventId, String eventType, long eventTimestamp,
                        long processingTimestamp, long windowStart, long windowEnd) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.eventTimestamp = eventTimestamp;
            this.processingTimestamp = processingTimestamp;
            this.latenessMs = processingTimestamp - eventTimestamp;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.reason = "Arrived after allowed lateness";
        }
    }

    // ===================== Functions =====================

    public static class CountAggregator implements AggregateFunction<ClickstreamEvent, Long, Long> {
        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(ClickstreamEvent event, Long accumulator) {
            return accumulator + 1;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    /**
     * Tracks whether this is a late update to detect window re-computation
     */
    public static class AddWindowMetadataWithLateness
            extends ProcessWindowFunction<Long, EventCount, String, TimeWindow> {

        private transient ValueState<Long> previousCount;

        @Override
        public void open(Configuration parameters) throws Exception {
            ValueStateDescriptor<Long> descriptor =
                new ValueStateDescriptor<>("previous-count", Long.class);
            previousCount = getRuntimeContext().getState(descriptor);
        }

        @Override
        public void process(String eventType, Context context,
                          Iterable<Long> counts,
                          Collector<EventCount> out) throws Exception {

            Long count = counts.iterator().next();
            TimeWindow window = context.window();

            // Check if this is an update (late data triggered re-computation)
            Long prev = previousCount.value();
            boolean isLateUpdate = prev != null && !prev.equals(count);

            // Update state
            previousCount.update(count);

            out.collect(new EventCount(
                window.getStart(),
                window.getEnd(),
                eventType,
                count,
                isLateUpdate
            ));

            if (isLateUpdate) {
                LOG.info("Late update detected: {} window [{}-{}] count: {} → {}",
                    eventType, window.getStart(), window.getEnd(), prev, count);
            }
        }
    }
}
