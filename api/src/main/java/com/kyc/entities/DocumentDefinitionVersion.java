package com.kyc.entities;

import com.kyc.enums.SchemaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "document_definition_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"definition_id", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentDefinitionVersion {

    @Id
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(nullable = false, length = 32)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SchemaStatus status;

    @Lob
    @Column(name = "schema_json", nullable = false, columnDefinition = "LONGTEXT")
    private String schemaJson;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "active_marker", unique = true, length = 36)
    private String activeMarker;

    public DocumentDefinitionVersion(
            UUID id, UUID definitionId, String version, SchemaStatus status, String schemaJson) {
        this.id = id;
        this.definitionId = definitionId;
        this.version = version;
        this.status = status;
        this.schemaJson = schemaJson;
    }

    public void activate(Instant now) {
        this.status = SchemaStatus.ACTIVE;
        this.activatedAt = now;
        this.retiredAt = null;
        this.activeMarker = id.toString();
    }

    public void retire(Instant now) {
        this.status = SchemaStatus.RETIRED;
        this.retiredAt = now;
        this.activeMarker = null;
    }
}
