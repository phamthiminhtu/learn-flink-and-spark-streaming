package com.streaming.flink.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.streaming.flink.model.ClickstreamEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Custom deserialization schema for converting Kafka JSON messages to ClickstreamEvent POJOs.
 *
 * This schema handles:
 * - JSON deserialization from Kafka value bytes
 * - Error handling for malformed JSON
 * - Type information for Flink's type system
 */
public class ClickstreamEventDeserializationSchema implements DeserializationSchema<ClickstreamEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(ClickstreamEventDeserializationSchema.class);

    private transient ObjectMapper objectMapper;

    /**
     * Initialize ObjectMapper for JSON deserialization.
     * Called once per task instance.
     */
    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            // Ignore unknown properties to be flexible with schema evolution
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            // Handle null values gracefully
            objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        }
        return objectMapper;
    }

    @Override
    public ClickstreamEvent deserialize(byte[] message) throws IOException {
        try {
            return getObjectMapper().readValue(message, ClickstreamEvent.class);
        } catch (IOException e) {
            LOG.error("Failed to deserialize message: {}", new String(message), e);
            // Return null for invalid messages - Flink will skip them
            // Alternative: throw exception to stop processing
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(ClickstreamEvent nextElement) {
        // Streaming never ends
        return false;
    }

    @Override
    public TypeInformation<ClickstreamEvent> getProducedType() {
        return TypeInformation.of(ClickstreamEvent.class);
    }
}
