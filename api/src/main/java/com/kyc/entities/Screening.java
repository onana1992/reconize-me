package com.kyc.entities;

import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.ScreeningStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "screenings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screening {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "verification_id")
    private UUID verificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScreeningStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ScreeningOutcome outcome;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(nullable = false, length = 16)
    private String engine;

    @Column(name = "policy_version", nullable = false, length = 32)
    private String policyVersion;

    @Column(name = "corpus_version", nullable = false, length = 32)
    private String corpusVersion;

    @Column(name = "retrieved_at")
    private Instant retrievedAt;

    @Column(name = "match_algorithm", length = 32)
    private String matchAlgorithm;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "provider_response", columnDefinition = "text")
    private String providerResponse;

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 128)
    private String lastName;

    @Column(name = "middle_name", length = 128)
    private String middleName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 2)
    private String nationality;

    @Column(name = "country_of_residence", length = 2)
    private String countryOfResidence;

    @Column(name = "id_number", length = 64)
    private String idNumber;

    @Column(name = "id_type", length = 32)
    private String idType;

    @Column(name = "normalized_subject", nullable = false, length = 512)
    private String normalizedSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Screening(
            UUID id,
            UUID organizationId,
            UUID integrationId,
            String externalId,
            UUID verificationId,
            ScreeningOutcome outcome,
            String policyVersion,
            String normalizedSubject,
            String firstName,
            String lastName,
            String middleName,
            LocalDate dateOfBirth,
            String nationality,
            String countryOfResidence,
            String idNumber,
            String idType,
            Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.integrationId = integrationId;
        this.externalId = externalId;
        this.verificationId = verificationId;
        this.status = ScreeningStatus.COMPLETED;
        this.outcome = outcome;
        this.engine = "aml-1";
        this.policyVersion = policyVersion;
        this.corpusVersion = "fixture-1";
        this.subjectType = "person";
        this.normalizedSubject = normalizedSubject;
        this.firstName = firstName;
        this.lastName = lastName;
        this.middleName = middleName;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.countryOfResidence = countryOfResidence;
        this.idNumber = idNumber;
        this.idType = idType;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rememberProviderResponse(String body) {
        this.providerResponse = body == null || body.isBlank() ? null : body;
    }

    public void useEngine(String engine, String corpusVersion, String matchAlgorithm, Instant retrievedAt) {
        this.engine = engine;
        this.corpusVersion = corpusVersion;
        this.matchAlgorithm = matchAlgorithm;
        this.retrievedAt = retrievedAt;
    }

    public void fail(String reason, Instant now) {
        this.status = ScreeningStatus.FAILED;
        this.outcome = null;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void replaceOutcome(ScreeningOutcome outcome, Instant now) {
        this.outcome = outcome;
        this.updatedAt = now;
    }
}
