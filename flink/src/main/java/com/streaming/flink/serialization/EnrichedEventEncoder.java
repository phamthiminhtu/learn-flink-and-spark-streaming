package com.streaming.flink.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.streaming.flink.model.EnrichedClickstreamEvent;
import org.apache.flink.api.common.serialization.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Encoder for serializing EnrichedClickstreamEvent objects to JSON format.
 * Used by FileSink to write data to S3/MinIO.
 *
 * Each event is written as a single JSON line (JSONL format).
 */
public class EnrichedEventEncoder implements Encoder<EnrichedClickstreamEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(EnrichedEventEncoder.class);

    private transient ObjectMapper objectMapper;

    /**
     * Initialize ObjectMapper for JSON serialization.
     */
    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // Pretty print disabled for compact storage
            objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        }
        return objectMapper;
    }

    @Override
    public void encode(EnrichedClickstreamEvent element, OutputStream stream) throws IOException {
        try {
            // Write JSON followed by newline (JSONL format)
            getObjectMapper().writeValue(stream, element);
            stream.write('\n');
        } catch (IOException e) {
            LOG.error("Failed to serialize event: {}", element, e);
            throw e;
        }
    }
}
