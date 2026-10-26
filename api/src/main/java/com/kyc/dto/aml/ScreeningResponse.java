package com.kyc.dto.aml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScreeningResponse(
        UUID id,
        @JsonProperty("integration_id") UUID integrationId,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("verification_id") UUID verificationId,
        String status,
        String outcome,
        @JsonProperty("failure_reason") String failureReason,
        String engine,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("corpus_version") String corpusVersion,
        @JsonProperty("retrieved_at") Instant retrievedAt,
        Subject subject,
        List<Hit> hits,
        @JsonProperty("hit_count") int hitCount,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("provider_response") String providerResponse) {

    public record Subject(
            String type,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("middle_name") String middleName,
            @JsonProperty("date_of_birth") String dateOfBirth,
            String nationality,
            @JsonProperty("country_of_residence") String countryOfResidence,
            @JsonProperty("id_number") String idNumber,
            @JsonProperty("id_type") String idType) {}

    public record Hit(
            UUID id,
            @JsonProperty("entity_id") String entityId,
            String category,
            List<String> categories,
            BigDecimal score,
            @JsonProperty("matched_name") String matchedName,
            @JsonProperty("listed_name") String listedName,
            List<String> aliases,
            @JsonProperty("birth_dates") List<String> birthDates,
            List<String> countries,
            List<String> datasets,
            List<String> features,
            @JsonProperty("provider_features") Map<String, BigDecimal> providerFeatures,
            String disposition,
            String reason,
            @JsonProperty("prior_disposition") String priorDisposition,
            @JsonProperty("prior_screening_id") UUID priorScreeningId) {}
}
