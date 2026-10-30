package com.kyc.entities;

import com.kyc.enums.AmlCategory;
import com.kyc.enums.HitDisposition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "screening_hits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreeningHit {

    @Id
    private UUID id;

    @Column(name = "screening_id", nullable = false)
    private UUID screeningId;

    @Column(name = "entity_id", nullable = false, length = 128)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AmlCategory category;

    @Column(nullable = false, length = 128)
    private String categories;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal score;

    @Column(name = "matched_name", nullable = false, length = 256)
    private String matchedName;

    @Column(name = "listed_name", nullable = false, length = 256)
    private String listedName;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "aliases_json", columnDefinition = "text")
    private String aliasesJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "birth_dates_json", columnDefinition = "text")
    private String birthDatesJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "countries_json", columnDefinition = "text")
    private String countriesJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "datasets_json", columnDefinition = "text")
    private String datasetsJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "features_json", columnDefinition = "text")
    private String featuresJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "provider_features_json", columnDefinition = "text")
    private String providerFeaturesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HitDisposition disposition;

    @Column(name = "disposition_reason", length = 64)
    private String dispositionReason;

    @Column(name = "disposition_note", length = 500)
    private String dispositionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "prior_disposition", length = 32)
    private HitDisposition priorDisposition;

    @Column(name = "prior_screening_id")
    private UUID priorScreeningId;

    public ScreeningHit(
            UUID id,
            UUID screeningId,
            String entityId,
            AmlCategory category,
            String categories,
            BigDecimal score,
            String matchedName,
            String listedName,
            String aliasesJson,
            String birthDatesJson,
            String countriesJson,
            String datasetsJson,
            String featuresJson) {
        this.id = id;
        this.screeningId = screeningId;
        this.entityId = entityId;
        this.category = category;
        this.categories = categories;
        this.score = score;
        this.matchedName = matchedName;
        this.listedName = listedName;
        this.aliasesJson = aliasesJson;
        this.birthDatesJson = birthDatesJson;
        this.countriesJson = countriesJson;
        this.datasetsJson = datasetsJson;
        this.featuresJson = featuresJson;
        this.providerFeaturesJson = "{}";
        this.disposition = HitDisposition.UNREVIEWED;
    }

    public void replaceProviderFeatures(String providerFeaturesJson) {
        this.providerFeaturesJson = providerFeaturesJson == null || providerFeaturesJson.isBlank() ? "{}" : providerFeaturesJson;
    }

    public void qualify(HitDisposition disposition, String reason, String note) {
        this.disposition = disposition;
        this.dispositionReason = reason;
        this.dispositionNote = note;
    }

    public void remember(HitDisposition prior, UUID priorScreeningId) {
        this.priorDisposition = prior;
        this.priorScreeningId = priorScreeningId;
    }
}
