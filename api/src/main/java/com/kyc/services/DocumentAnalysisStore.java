package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.documentia.DocumentScores;
import com.kyc.dto.documentia.MrzReport;
import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ParsedDocument.Classification;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.entities.VerificationDocumentAnalysis;
import com.kyc.entities.VerificationDocumentAnalysisKey;
import com.kyc.enums.VerificationDecision;
import com.kyc.repositories.VerificationDocumentAnalysisRepository;
import com.kyc.services.documentia.DocumentLiveView;
import com.kyc.services.documentia.DocumentValidator;
import com.kyc.services.documentia.SchemaEdition;
import com.kyc.services.documentia.SchemaRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Insère le dossier une fois par face. Une désactivation de schéma ne réécrit pas la ligne. */
@Service
public class DocumentAnalysisStore {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisStore.class);

    private final VerificationDocumentAnalysisRepository analyses;
    private final ObjectMapper objectMapper;

    public DocumentAnalysisStore(VerificationDocumentAnalysisRepository analyses, ObjectMapper objectMapper) {
        this.analyses = analyses;
        this.objectMapper = objectMapper;
    }

    public Optional<VerificationDocumentAnalysis> find(UUID verificationId, String side) {
        return analyses.findByVerificationIdAndSide(verificationId, side);
    }

    public void saveAccepted(UUID verificationId, DocumentLiveView view, String side, Instant now) {
        if (view == null || !view.accepted() || side == null || side.isBlank()) {
            return;
        }
        if (analyses.existsById(new VerificationDocumentAnalysisKey(verificationId, side))) {
            return;
        }
        ParsedDocument parsed = (ParsedDocument) view.response().parsed();
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("documentDetected", parsed.documentDetected());
        detection.put("detection", parsed.detection());
        detection.put("quality", parsed.quality());
        analyses.save(new VerificationDocumentAnalysis(
                verificationId,
                side,
                view.response().schemaVersionId(),
                "vision_llm",
                view.modelId(),
                json(detection),
                json(parsed.classification()),
                json(view.fields()),
                json(view.response().mrz()),
                json(view.validation()),
                json(view.response().indicators()),
                json(view.response().scores()),
                now));
        log.info(
                "document-ia stored verificationId={} schemaVersionId={} side={} provider=vision_llm",
                verificationId,
                view.response().schemaVersionId(),
                side);
    }

    public IdvDecisionEngine.Result replay(VerificationDocumentAnalysis row, SchemaRegistry registry, IdvDecisionEngine engine) {
        try {
            Classification classification = read(row.getClassificationJson(), Classification.class);
            List<NormalizedField> fields = readList(row.getFieldsJson(), new TypeReference<>() {});
            List<ValidationIssue> validation = readList(row.getValidationJson(), new TypeReference<>() {});
            MrzReport mrz = read(row.getMrzJson(), MrzReport.class);
            List<String> indicators = readList(row.getIndicatorsJson(), new TypeReference<>() {});
            DocumentScores scores = read(row.getScoresJson(), DocumentScores.class);
            SchemaEdition edition = registry.edition(row.getSchemaVersionId()).orElse(null);
            Map<String, Object> identity = identity(edition, classification, fields);
            ParsedDocument document = new ParsedDocument(
                    true,
                    "DOCUMENT_PRESENT",
                    null,
                    classification,
                    null,
                    List.of(),
                    List.of(),
                    indicators,
                    identity);
            DocumentValidator.Prepared prepared = DocumentValidator.prepare(
                    edition, document, fields, validation, mrz, true, null);
            String band = scores == null ? null : scores.band();
            IdvDecisionEngine.VisionFacts facts = prepared.facts();
            prepared = new DocumentValidator.Prepared(
                    prepared.validation(),
                    prepared.indicators(),
                    new IdvDecisionEngine.VisionFacts(
                            facts.readable(),
                            facts.captureReason(),
                            facts.expired(),
                            facts.unknown(),
                            facts.missingRequired(),
                            facts.mrzFailed(),
                            facts.lowOcr(),
                            facts.suspicious(),
                            band,
                            facts.hasError(),
                            facts.hasWarning(),
                            facts.extractedIdentity()),
                    prepared.signals());
            IdvDecisionEngine.Result result = engine.decideVision(prepared.signals(), prepared.facts()).result();
            if (result.decision() == null) {
                return new IdvDecisionEngine.Result(
                        VerificationDecision.REVIEW, result.reasons(), result.signals(), result.extractedIdentity());
            }
            return result;
        } catch (RuntimeException e) {
            log.info("document-ia replay skipped verificationId={} side={}", row.getVerificationId(), row.getSide());
            return engine.review("provider_unavailable");
        }
    }

    private Map<String, Object> identity(
            SchemaEdition edition, Classification classification, List<NormalizedField> fields) {
        Map<String, Object> identity = new LinkedHashMap<>();
        put(identity, "first_name", value(fields, "firstName"));
        put(identity, "last_name", value(fields, "lastName"));
        put(identity, "birth_date", value(fields, "dateOfBirth"));
        put(identity, "document_number", value(fields, "documentNumber"));
        put(identity, "expiration_date", value(fields, "expirationDate"));
        if (edition != null) {
            identity.put("document_type", edition.documentType().toLowerCase(Locale.ROOT));
            identity.put("document_country", edition.country());
            if (edition.issuingJurisdiction() != null) {
                identity.put("issuing_jurisdiction", edition.issuingJurisdiction());
            }
            identity.put("schema_version", edition.version());
        }
        if (classification != null && classification.code() != null) {
            identity.put("document_code", classification.code());
        }
        return Map.copyOf(identity);
    }

    private static String value(List<NormalizedField> fields, String name) {
        if (fields == null) {
            return null;
        }
        for (NormalizedField field : fields) {
            if (name.equals(field.field()) && "VALID".equals(field.validationStatus())) {
                return field.normalizedValue();
            }
        }
        return null;
    }

    private static void put(Map<String, Object> identity, String key, String value) {
        if (value != null && !value.isBlank()) {
            identity.put(key, value);
        }
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.info("document-ia store skipped a json block");
            return null;
        }
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored json");
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> values = objectMapper.readValue(json, type);
            return values == null ? List.of() : values;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored json");
        }
    }
}
