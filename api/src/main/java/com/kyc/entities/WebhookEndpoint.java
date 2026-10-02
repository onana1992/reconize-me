package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "webhook_endpoints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEndpoint {

    public static final String ACTIVE = "active";
    public static final String DISABLED = "disabled";

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "secret_cipher", nullable = false, length = 512)
    private String secretCipher;

    @Column(name = "secret_prefix", nullable = false, length = 16)
    private String secretPrefix;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WebhookEndpoint(
            UUID id,
            UUID organizationId,
            UUID integrationId,
            String url,
            String secretCipher,
            String secretPrefix,
            Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.integrationId = integrationId;
        this.url = url;
        this.secretCipher = secretCipher;
        this.secretPrefix = secretPrefix;
        this.status = ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateUrl(String url, Instant now) {
        this.url = url;
        this.updatedAt = now;
    }

    public void rotateSecret(String secretCipher, String secretPrefix, Instant now) {
        this.secretCipher = secretCipher;
        this.secretPrefix = secretPrefix;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
