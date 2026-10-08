package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verification_document_analyses")
@IdClass(VerificationDocumentAnalysisKey.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationDocumentAnalysis {

    @Id
    @Column(name = "verification_id")
    private UUID verificationId;

    @Id
    @Column(nullable = false, length = 16)
    private String side;

    @Column(name = "schema_version_id")
    private UUID schemaVersionId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "model_id", nullable = false, length = 64)
    private String modelId;

    @Lob
    @Column(name = "detection_json", columnDefinition = "LONGTEXT")
    private String detectionJson;

    @Lob
    @Column(name = "classification_json", columnDefinition = "LONGTEXT")
    private String classificationJson;

    @Lob
    @Column(name = "fields_json", columnDefinition = "LONGTEXT")
    private String fieldsJson;

    @Lob
    @Column(name = "mrz_json", columnDefinition = "LONGTEXT")
    private String mrzJson;

    @Lob
    @Column(name = "validation_json", columnDefinition = "LONGTEXT")
    private String validationJson;

    @Lob
    @Column(name = "indicators_json", columnDefinition = "LONGTEXT")
    private String indicatorsJson;

    @Lob
    @Column(name = "scores_json", columnDefinition = "LONGTEXT")
    private String scoresJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VerificationDocumentAnalysis(
            UUID verificationId,
            String side,
            UUID schemaVersionId,
            String provider,
            String modelId,
            String detectionJson,
            String classificationJson,
            String fieldsJson,
            String mrzJson,
            String validationJson,
            String indicatorsJson,
            String scoresJson,
            Instant createdAt) {
        this.verificationId = verificationId;
        this.side = side;
        this.schemaVersionId = schemaVersionId;
        this.provider = provider;
        this.modelId = modelId;
        this.detectionJson = detectionJson;
        this.classificationJson = classificationJson;
        this.fieldsJson = fieldsJson;
        this.mrzJson = mrzJson;
        this.validationJson = validationJson;
        this.indicatorsJson = indicatorsJson;
        this.scoresJson = scoresJson;
        this.createdAt = createdAt;
    }
}
