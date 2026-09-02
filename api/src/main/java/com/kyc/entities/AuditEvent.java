package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            UUID organizationId,
            String actorType,
            UUID actorId,
            String action,
            String resourceType,
            UUID resourceId,
            String payload,
            Instant createdAt) {
        this.organizationId = organizationId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getPayload() {
        return payload;
    }
}
