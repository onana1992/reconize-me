package com.kyc.entities;

import com.kyc.enums.MediaKind;
import com.kyc.enums.MediaStatus;
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
@Table(name = "authentication_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthenticationMedia {

    @Id
    private UUID id;

    @Column(name = "authentication_id", nullable = false)
    private UUID authenticationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaKind kind;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuthenticationMedia(
            UUID id, UUID authenticationId, MediaKind kind, int attempt, String objectKey, Instant createdAt) {
        this.id = id;
        this.authenticationId = authenticationId;
        this.kind = kind;
        this.attempt = attempt;
        this.objectKey = objectKey;
        this.status = MediaStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void accept(String contentType, long byteSize) {
        this.status = MediaStatus.ACCEPTED;
        this.contentType = contentType;
        this.byteSize = byteSize;
    }
}
