package com.streaming.flink.exercises;

import com.streaming.flink.model.ClickstreamEvent;
import com.streaming.flink.serialization.ClickstreamEventDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Exercise 2.1 & 2.2: Tumbling Windows
 *
 * Part 1: Count events per 5-minute window grouped by event_type
 * Part 2: Top pages per window
 *
 * Key Concepts:
 * - TumblingEventTimeWindows: Fixed-size, non-overlapping windows
 * - AggregateFunction: Efficient incremental aggregation
 * - ProcessWindowFunction: Add window metadata to results
 * - Combining both: .aggregate(agg, process) for efficiency + metadata
 */
public class TumblingWindowExercise {

    private static final Logger LOG = LoggerFactory.getLogger(TumblingWindowExercise.class);

    private static final String KAFKA_BOOTSTRAP_SERVERS =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092");
    private static final String KAFKA_TOPIC = "clickstream";

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        LOG.info("Starting Tumbling Window Exercise");
        LOG.info("Window size: 5 minutes");

        // Create Kafka source
        KafkaSource<ClickstreamEvent> source = KafkaSource.<ClickstreamEvent>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(KAFKA_TOPIC)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new ClickstreamEventDeserializationSchema())
            .setProperty("group.id", "tumbling-window-exercise")
            .build();

        // Read from Kafka with watermark
        DataStream<ClickstreamEvent> events = env.fromSource(
            source,
            WatermarkStrategy
                .<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofMinutes(10))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp()),
            "Kafka Source"
        ).filter(event -> event != null);

        // PART 1: Count events per event_type per 5-minute window
        DataStream<EventTypeCount> eventTypeCounts = events
            .keyBy(ClickstreamEvent::getEventType)
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .aggregate(
                new CountAggregator(),
                new AddWindowMetadata()
            );

        // Sink Part 1 to Postgres
        String jdbcUrl = "jdbc:postgresql://postgres:5432/streaming";
        String insertSQL1 =
            "INSERT INTO event_counts_5min " +
            "(window_start, window_end, event_type, count) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (window_start, event_type) DO UPDATE SET " +
            "count = EXCLUDED.count";

        eventTypeCounts.addSink(
            JdbcSink.sink(
                insertSQL1,
                (JdbcStatementBuilder<EventTypeCount>) (statement, count) -> {
                    statement.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(count.windowStart)));
                    statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(count.windowEnd)));
                    statement.setString(3, count.eventType);
                    statement.setLong(4, count.count);
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

        // PART 2: Top pages per window
        DataStream<PageViewCount> pageViewCounts = events
            .filter(e -> "page_view".equals(e.getEventType()))
            .keyBy(ClickstreamEvent::getPageUrl)
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .aggregate(
                new CountAggregator(),
                new AddPageWindowMetadata()
            );

        // For top-N, you would typically:
        // 1. Use a ProcessAllWindowFunction to collect all pages in the window
        // 2. Sort and emit top N
        // Here we just emit all counts - you can query for top N in SQL

        String insertSQL2 =
            "INSERT INTO page_views_5min " +
            "(window_start, window_end, page_url, count) " +
            "VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (window_start, page_url) DO UPDATE SET " +
            "count = EXCLUDED.count";

        pageViewCounts.addSink(
            JdbcSink.sink(
                insertSQL2,
                (JdbcStatementBuilder<PageViewCount>) (statement, count) -> {
                    statement.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(count.windowStart)));
                    statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(count.windowEnd)));
                    statement.setString(3, count.pageUrl);
                    statement.setLong(4, count.count);
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

        LOG.info("Results will be written to:");
        LOG.info("  - event_counts_5min (event type counts)");
        LOG.info("  - page_views_5min (page view counts)");

        env.execute("Tumbling Window Exercise");
    }

    // ===================== POJOs =====================

    public static class EventTypeCount {
        public long windowStart;
        public long windowEnd;
        public String eventType;
        public long count;

        public EventTypeCount() {}

        public EventTypeCount(long windowStart, long windowEnd, String eventType, long count) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.eventType = eventType;
            this.count = count;
        }
    }

    public static class PageViewCount {
        public long windowStart;
        public long windowEnd;
        public String pageUrl;
        public long count;

        public PageViewCount() {}

        public PageViewCount(long windowStart, long windowEnd, String pageUrl, long count) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.pageUrl = pageUrl;
            this.count = count;
        }
    }

    // ===================== Functions =====================

    /**
     * Efficient incremental aggregation - counts events as they arrive
     * Much more efficient than ProcessWindowFunction alone
     */
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
     * Adds window start/end metadata to the count
     * Used in combination with AggregateFunction for efficiency
     */
    public static class AddWindowMetadata
            extends ProcessWindowFunction<Long, EventTypeCount, String, TimeWindow> {

        @Override
        public void process(String eventType, Context context,
                          Iterable<Long> counts,
                          Collector<EventTypeCount> out) {
            Long count = counts.iterator().next();
            TimeWindow window = context.window();

            out.collect(new EventTypeCount(
                window.getStart(),
                window.getEnd(),
                eventType,
                count
            ));
        }
    }

    /**
     * Similar to AddWindowMetadata but for page URLs
     */
    public static class AddPageWindowMetadata
            extends ProcessWindowFunction<Long, PageViewCount, String, TimeWindow> {

        @Override
        public void process(String pageUrl, Context context,
                          Iterable<Long> counts,
                          Collector<PageViewCount> out) {
            Long count = counts.iterator().next();
            TimeWindow window = context.window();

            out.collect(new PageViewCount(
                window.getStart(),
                window.getEnd(),
                pageUrl,
                count
            ));
        }
    }
}
