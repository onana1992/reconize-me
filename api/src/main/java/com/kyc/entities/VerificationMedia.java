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
@Table(name = "verification_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationMedia {

    @Id
    private UUID id;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaKind kind;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VerificationMedia(
            UUID id,
            UUID verificationId,
            MediaKind kind,
            int attempt,
            String objectKey,
            Instant createdAt) {
        this.id = id;
        this.verificationId = verificationId;
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

    public void rejectQuality() {
        this.status = MediaStatus.REJECTED_QUALITY;
    }
}
