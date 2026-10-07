package com.kyc.entities;

import com.kyc.enums.MrzFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentDefinition {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;

    @Column(nullable = false, length = 16)
    private String side;

    @Enumerated(EnumType.STRING)
    @Column(name = "mrz_format", nullable = false, length = 8)
    private MrzFormat mrzFormat;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DocumentDefinition(
            UUID id,
            String code,
            String country,
            String documentType,
            String side,
            MrzFormat mrzFormat,
            boolean enabled,
            Instant createdAt) {
        this.id = id;
        this.code = code;
        this.country = country;
        this.documentType = documentType;
        this.side = side;
        this.mrzFormat = mrzFormat;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
