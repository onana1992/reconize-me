package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "detection_json", columnDefinition = "text")
    private String detectionJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "classification_json", columnDefinition = "text")
    private String classificationJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "fields_json", columnDefinition = "text")
    private String fieldsJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "mrz_json", columnDefinition = "text")
    private String mrzJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "validation_json", columnDefinition = "text")
    private String validationJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "indicators_json", columnDefinition = "text")
    private String indicatorsJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "scores_json", columnDefinition = "text")
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
