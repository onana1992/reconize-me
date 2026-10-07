package com.kyc.services.documentia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.documentia.DocumentParse;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ParsedDocument.BoundingBox;
import com.kyc.dto.documentia.ParsedDocument.Classification;
import com.kyc.dto.documentia.ParsedDocument.ExtractedField;
import com.kyc.dto.documentia.ParsedDocument.Quality;
import com.kyc.dto.documentia.ParsedDocument.Zone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentAnalysisParser {

    static final Set<String> DETECTIONS = Set.of(
            "NO_DOCUMENT",
            "DOCUMENT_PRESENT",
            "PARTIALLY_VISIBLE",
            "CROPPED",
            "MULTIPLE_DOCUMENTS",
            "TOO_SMALL",
            "UNREADABLE");

    static final double LOW_CLASS_CONFIDENCE = 0.80;

    private static final Set<String> SOURCES = Set.of("VISUAL_TEXT", "MRZ", "VISUAL_AND_MRZ");

    private final ObjectMapper objectMapper;
    private final SchemaCatalog catalog;

    public DocumentAnalysisParser(ObjectMapper objectMapper, SchemaCatalog catalog) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
    }

    public DocumentParse parse(String json) {
        JsonNode root = readObject(json);
        String detection = requiredDetection(root);
        Quality quality = quality(root.get("quality"), detection);
        Resolved resolved = classification(root.get("classification"));
        ParsedDocument document = new ParsedDocument(
                !"NO_DOCUMENT".equals(detection),
                detection,
                quality,
                resolved.classification(),
                textOrNull(root.get("rawText")),
                zones(root.get("zones")),
                fields(root.get("fields"), resolved.fieldNames()),
                resolved.indicators());
        return new DocumentParse(document, resolved.schemaVersionId());
    }

    private JsonNode readObject(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidModelJsonException("Model JSON is not valid");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidModelJsonException("Model JSON is not an object");
        }
        return root;
    }

    private static String requiredDetection(JsonNode root) {
        JsonNode node = root.get("detection");
        if (node == null || !node.isTextual() || !DETECTIONS.contains(node.asText())) {
            throw new InvalidModelJsonException("detection is not allowed");
        }
        return node.asText();
    }

    private static Quality quality(JsonNode node, String detection) {
        if (node != null && !node.isObject()) {
            throw new InvalidModelJsonException("quality is not an object");
        }
        boolean blur = flag(node, "blur");
        boolean glare = flag(node, "glare");
        boolean cropped = flag(node, "cropped");
        boolean partiallyVisible = flag(node, "partiallyVisible");
        boolean multipleDocuments = flag(node, "multipleDocuments");
        boolean tooSmall = flag(node, "tooSmall");
        boolean lowLight = flag(node, "lowLight");
        boolean overexposed = flag(node, "overexposed");
        boolean tilted = flag(node, "tilted");
        boolean severe = blur
                || glare
                || cropped
                || partiallyVisible
                || multipleDocuments
                || tooSmall
                || lowLight
                || overexposed;
        boolean readable = "DOCUMENT_PRESENT".equals(detection) && !severe;
        boolean documentDetected = !"NO_DOCUMENT".equals(detection);
        return new Quality(
                readable,
                blur,
                glare,
                cropped,
                partiallyVisible,
                multipleDocuments,
                tooSmall,
                lowLight,
                overexposed,
                tilted,
                documentDetected && !readable);
    }

    private Resolved classification(JsonNode node) {
        if (node != null && !node.isObject()) {
            throw new InvalidModelJsonException("classification is not an object");
        }
        String code = node == null ? null : textOrNull(node.get("code"));
        List<ActiveSchema> schemas = catalog.findActives(code);
        Double confidence = node == null ? null : unitInterval(node.get("confidence"));
        if (schemas.isEmpty()) {
            return new Resolved(
                    new Classification("UNKNOWN", "ZZ", null, "UNKNOWN", null, "UNKNOWN", confidence),
                    null,
                    null,
                    List.of());
        }
        ActiveSchema reference = schemas.get(0);
        String side = textOrNull(node.get("side"));
        if (!reference.allows(side)) {
            String only = reference.onlySide();
            if (only != null) {
                side = only;
            }
        }
        String modelVersion = textOrNull(node.get("version"));
        ActiveSchema matched = schemas.stream()
                .filter(schema -> schema.version().equals(modelVersion))
                .findFirst()
                .orElse(null);
        List<String> indicators = new ArrayList<>();
        UUID schemaVersionId;
        Set<String> fieldNames;
        String version;
        String jurisdiction;
        if (matched == null) {
            indicators.add("unexpected_document_structure");
            schemaVersionId = null;
            version = modelVersion;
            jurisdiction = reference.issuingJurisdiction();
            Set<String> union = new HashSet<>();
            for (ActiveSchema schema : schemas) {
                union.addAll(schema.fieldNames());
            }
            fieldNames = Set.copyOf(union);
        } else {
            schemaVersionId = matched.versionId();
            version = matched.version();
            jurisdiction = matched.issuingJurisdiction();
            fieldNames = matched.fieldNames();
        }
        if (confidence != null && confidence < LOW_CLASS_CONFIDENCE) {
            indicators.add("DOCUMENT_UNKNOWN");
        }
        return new Resolved(
                new Classification(
                        reference.code(),
                        reference.country(),
                        jurisdiction,
                        reference.documentType(),
                        version,
                        side,
                        confidence),
                schemaVersionId,
                fieldNames,
                List.copyOf(indicators));
    }

    private static List<Zone> zones(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new InvalidModelJsonException("zones is not an array");
        }
        List<Zone> zones = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new InvalidModelJsonException("zone is not an object");
            }
            BoundingBox box = box(item.get("boundingBox"));
            zones.add(new Zone(
                    textOrNull(item.get("id")),
                    textOrNull(item.get("text")),
                    unitInterval(item.get("confidence")),
                    box,
                    box == null ? null : "APPROXIMATE",
                    box == null ? null : "MODEL_ESTIMATE"));
        }
        return List.copyOf(zones);
    }

    private static List<ExtractedField> fields(JsonNode node, Set<String> allowed) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new InvalidModelJsonException("fields is not an array");
        }
        List<ExtractedField> fields = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new InvalidModelJsonException("field is not an object");
            }
            String name = textOrNull(item.get("field"));
            if (name == null || name.isBlank()) {
                throw new InvalidModelJsonException("field name is required");
            }
            if (allowed == null || !allowed.contains(name)) {
                continue;
            }
            fields.add(new ExtractedField(name, value(item.get("value")), unitInterval(item.get("confidence")), source(item)));
        }
        return List.copyOf(fields);
    }

    private record Resolved(
            Classification classification, UUID schemaVersionId, Set<String> fieldNames, List<String> indicators) {}

    private static BoundingBox box(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        Double x = unit(node.get("x"));
        Double y = unit(node.get("y"));
        Double width = unit(node.get("width"));
        Double height = unit(node.get("height"));
        if (x == null || y == null || width == null || height == null) {
            return null;
        }
        return new BoundingBox(x, y, width, height);
    }

    private static String source(JsonNode field) {
        String source = textOrNull(field.get("source"));
        return source != null && SOURCES.contains(source) ? source : null;
    }

    private static String value(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        throw new InvalidModelJsonException("field value is not text");
    }

    private static boolean flag(JsonNode quality, String name) {
        if (quality == null) {
            return false;
        }
        JsonNode node = quality.get(name);
        return node != null && node.isBoolean() && node.booleanValue();
    }

    private static Double unitInterval(JsonNode node) {
        return unit(node);
    }

    private static Double unit(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double value = node.doubleValue();
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            return null;
        }
        return value;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }
}
