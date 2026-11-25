package com.sentinel.sentinel.enrich;

import com.sentinel.sentinel.ingest.message.FirewallEvent;
import com.sentinel.sentinel.ingest.message.LoginEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class StoredEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "source_ip", nullable = false, length = 45)
    private String sourceIp;

    @Column(name = "known_bad_ip", nullable = false)
    private boolean knownBadIp;

    @Column(name = "username", length = 256)
    private String user;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "destination_ip", length = 45)
    private String destinationIp;

    @Column(name = "destination_port")
    private Integer destinationPort;

    @Column(name = "action", length = 5)
    private String action;

    @Column(name = "stored_at", insertable = false, updatable = false)
    private Instant storedAt;

    protected StoredEvent() {
    }

    private StoredEvent(
            String customerId,
            String type,
            Instant eventTime,
            String sourceIp,
            boolean knownBadIp,
            String user,
            Boolean success,
            String destinationIp,
            Integer destinationPort,
            String action) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.type = type;
        this.eventTime = eventTime;
        this.sourceIp = sourceIp;
        this.knownBadIp = knownBadIp;
        this.user = user;
        this.success = success;
        this.destinationIp = destinationIp;
        this.destinationPort = destinationPort;
        this.action = action;
    }

    public static StoredEvent fromLogin(String customerId, LoginEvent event, boolean knownBad) {
        return new StoredEvent(
                customerId,
                "LOGIN",
                event.timestamp(),
                event.sourceIp(),
                knownBad,
                event.user(),
                event.success(),
                null,
                null,
                null);
    }

    public static StoredEvent fromFirewall(String customerId, FirewallEvent event, boolean knownBad) {
        return new StoredEvent(
                customerId,
                "FIREWALL",
                event.timestamp(),
                event.sourceIp(),
                knownBad,
                null,
                null,
                event.destinationIp(),
                event.destinationPort(),
                event.action());
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getType() {
        return type;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public boolean isKnownBadIp() {
        return knownBadIp;
    }

    public String getUser() {
        return user;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public Integer getDestinationPort() {
        return destinationPort;
    }

    public String getAction() {
        return action;
    }

    public Instant getStoredAt() {
        return storedAt;
    }
}
