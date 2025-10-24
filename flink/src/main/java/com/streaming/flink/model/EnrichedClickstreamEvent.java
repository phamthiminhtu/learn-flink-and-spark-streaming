package com.streaming.flink.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Enriched clickstream event with window metadata.
 * This class extends the base event with window start/end times,
 * matching the Spark implementation output.
 */
public class EnrichedClickstreamEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // Original event fields
    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("page_url")
    private String pageUrl;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("user_agent")
    private String userAgent;

    @JsonProperty("referrer")
    private String referrer;

    @JsonProperty("country")
    private String country;

    @JsonProperty("device_type")
    private String deviceType;

    // Enriched fields
    @JsonProperty("event_timestamp")
    private String eventTimestamp;  // ISO-8601 timestamp

    @JsonProperty("event_timestamp_start")
    private String eventTimestampStart;  // Window start time

    @JsonProperty("event_timestamp_end")
    private String eventTimestampEnd;  // Window end time

    public EnrichedClickstreamEvent() {
    }

    /**
     * Create enriched event from base event and window boundaries
     */
    public static EnrichedClickstreamEvent fromEvent(ClickstreamEvent event, long windowStart, long windowEnd) {
        EnrichedClickstreamEvent enriched = new EnrichedClickstreamEvent();
        enriched.eventId = event.getEventId();
        enriched.userId = event.getUserId();
        enriched.sessionId = event.getSessionId();
        enriched.eventType = event.getEventType();
        enriched.pageUrl = event.getPageUrl();
        enriched.timestamp = event.getTimestamp();
        enriched.ipAddress = event.getIpAddress();
        enriched.userAgent = event.getUserAgent();
        enriched.referrer = event.getReferrer();
        enriched.country = event.getCountry();
        enriched.deviceType = event.getDeviceType();

        // Convert timestamps to ISO-8601 format
        enriched.eventTimestamp = Instant.ofEpochMilli(event.getTimestamp()).toString();
        enriched.eventTimestampStart = Instant.ofEpochMilli(windowStart).toString();
        enriched.eventTimestampEnd = Instant.ofEpochMilli(windowEnd).toString();

        return enriched;
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(String eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getEventTimestampStart() {
        return eventTimestampStart;
    }

    public void setEventTimestampStart(String eventTimestampStart) {
        this.eventTimestampStart = eventTimestampStart;
    }

    public String getEventTimestampEnd() {
        return eventTimestampEnd;
    }

    public void setEventTimestampEnd(String eventTimestampEnd) {
        this.eventTimestampEnd = eventTimestampEnd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnrichedClickstreamEvent that = (EnrichedClickstreamEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "EnrichedClickstreamEvent{" +
                "eventId='" + eventId + '\'' +
                ", userId='" + userId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", pageUrl='" + pageUrl + '\'' +
                ", timestamp=" + timestamp +
                ", eventTimestamp='" + eventTimestamp + '\'' +
                ", eventTimestampStart='" + eventTimestampStart + '\'' +
                ", eventTimestampEnd='" + eventTimestampEnd + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", country='" + country + '\'' +
                ", deviceType='" + deviceType + '\'' +
                '}';
    }
}
