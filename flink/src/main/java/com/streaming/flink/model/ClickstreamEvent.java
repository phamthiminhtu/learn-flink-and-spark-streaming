package com.streaming.flink.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Objects;

/**
 * POJO representing a clickstream event.
 * Maps to the Avro schema in data/schemas/clickstream-event-readable.avsc
 *
 * This class is used for deserialization from Kafka and processing in Flink DataStream.
 */
public class ClickstreamEvent implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private Long timestamp;  // timestamp-millis (epoch milliseconds)

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

    // Default constructor required for deserialization
    public ClickstreamEvent() {
    }

    // Constructor with all fields
    public ClickstreamEvent(String eventId, String userId, String sessionId,
                           String eventType, String pageUrl, Long timestamp,
                           String ipAddress, String userAgent, String referrer,
                           String country, String deviceType) {
        this.eventId = eventId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.pageUrl = pageUrl;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.country = country;
        this.deviceType = deviceType;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClickstreamEvent that = (ClickstreamEvent) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(sessionId, that.sessionId) &&
               Objects.equals(eventType, that.eventType) &&
               Objects.equals(pageUrl, that.pageUrl) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(ipAddress, that.ipAddress) &&
               Objects.equals(userAgent, that.userAgent) &&
               Objects.equals(referrer, that.referrer) &&
               Objects.equals(country, that.country) &&
               Objects.equals(deviceType, that.deviceType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, userId, sessionId, eventType, pageUrl,
                          timestamp, ipAddress, userAgent, referrer, country, deviceType);
    }

    @Override
    public String toString() {
        return "ClickstreamEvent{" +
                "eventId='" + eventId + '\'' +
                ", userId='" + userId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", pageUrl='" + pageUrl + '\'' +
                ", timestamp=" + timestamp +
                ", ipAddress='" + ipAddress + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", referrer='" + referrer + '\'' +
                ", country='" + country + '\'' +
                ", deviceType='" + deviceType + '\'' +
                '}';
    }
}
