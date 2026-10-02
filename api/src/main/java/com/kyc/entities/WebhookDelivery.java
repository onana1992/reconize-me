package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "webhook_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDelivery {

    public static final String PENDING = "pending";
    public static final String DELIVERED = "delivered";
    public static final String FAILED = "failed";
    public static final String EVENT_COMPLETED = "verification.completed";

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "decision_fingerprint", nullable = false, length = 64)
    private String decisionFingerprint;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_code", length = 32)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WebhookDelivery(
            UUID id,
            UUID organizationId,
            UUID integrationId,
            UUID endpointId,
            UUID verificationId,
            String decisionFingerprint,
            String payloadJson,
            String payloadHash,
            Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.integrationId = integrationId;
        this.endpointId = endpointId;
        this.verificationId = verificationId;
        this.eventType = EVENT_COMPLETED;
        this.decisionFingerprint = decisionFingerprint;
        this.payloadJson = payloadJson;
        this.payloadHash = payloadHash;
        this.attempt = 0;
        this.status = PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markDelivered(int httpStatus, Instant now) {
        this.status = DELIVERED;
        this.httpStatus = httpStatus;
        this.lastErrorCode = null;
        this.updatedAt = now;
    }

    public void markAttempt(int attempt, Integer httpStatus, String errorCode, Instant nextAttemptAt, Instant now) {
        this.attempt = attempt;
        this.httpStatus = httpStatus;
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
        if (attempt >= 5) {
            this.status = FAILED;
        } else {
            this.status = PENDING;
        }
    }

    public void requeue(Instant now) {
        this.status = PENDING;
        this.attempt = 0;
        this.nextAttemptAt = now;
        this.lastErrorCode = null;
        this.httpStatus = null;
        this.updatedAt = now;
    }
}
