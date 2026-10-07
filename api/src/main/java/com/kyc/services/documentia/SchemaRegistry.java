package com.kyc.services.documentia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.documentia.DocumentIaCatalogResponse;
import com.kyc.dto.documentia.DocumentIaCatalogResponse.Schema;
import com.kyc.entities.DocumentDefinition;
import com.kyc.entities.DocumentDefinitionVersion;
import com.kyc.entities.DocumentField;
import com.kyc.entities.DocumentValidationRule;
import com.kyc.enums.FieldValueType;
import com.kyc.enums.MrzFormat;
import com.kyc.enums.RuleLevel;
import com.kyc.enums.RuleSeverity;
import com.kyc.enums.SchemaStatus;
import com.kyc.repositories.DocumentDefinitionRepository;
import com.kyc.repositories.DocumentDefinitionVersionRepository;
import com.kyc.repositories.DocumentFieldRepository;
import com.kyc.repositories.DocumentValidationRuleRepository;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaRegistry implements SchemaCatalog {

    public static final UUID QUEBEC_DEFINITION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000a1");
    public static final UUID QUEBEC_VERSION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000a2");
    public static final String QUEBEC_CODE = "QUEBEC_DRIVER_LICENSE";
    public static final String QUEBEC_VERSION = "2024";

    static final String QUEBEC_SCHEMA_JSON =
            """
            {
              "code": "QUEBEC_DRIVER_LICENSE",
              "country": "CA",
              "documentType": "DRIVING_LICENSE",
              "version": "2024",
              "side": "FRONT",
              "mrzFormat": "NONE",
              "issuingJurisdiction": "QC",
              "fields": [
                {"name": "firstName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME"},
                {"name": "lastName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME"},
                {"name": "dateOfBirth", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy"]},
                {"name": "expirationDate", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy"]},
                {"name": "documentNumber", "type": "DOCUMENT_NUMBER", "required": true}
              ],
              "rules": []
            }
            """;

    private static final Pattern COMPARISON =
            Pattern.compile("^([A-Za-z][A-Za-z0-9]*)\\s*(<=|>=|<|>)\\s*([A-Za-z][A-Za-z0-9]*)$");

    private final DocumentDefinitionRepository definitions;
    private final DocumentDefinitionVersionRepository versions;
    private final DocumentFieldRepository fields;
    private final DocumentValidationRuleRepository rules;
    private final ObjectMapper objectMapper;

    public SchemaRegistry(
            DocumentDefinitionRepository definitions,
            DocumentDefinitionVersionRepository versions,
            DocumentFieldRepository fields,
            DocumentValidationRuleRepository rules,
            ObjectMapper objectMapper) {
        this.definitions = definitions;
        this.versions = versions;
        this.fields = fields;
        this.rules = rules;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureQuebecLicense() {
        if (definitions.findByCode(QUEBEC_CODE).isPresent()) {
            return;
        }
        Instant now = Instant.parse("2026-10-06T16:00:00Z");
        definitions.save(new DocumentDefinition(
                QUEBEC_DEFINITION_ID,
                QUEBEC_CODE,
                "CA",
                "DRIVING_LICENSE",
                "FRONT",
                MrzFormat.NONE,
                true,
                now));
        versions.save(new DocumentDefinitionVersion(
                QUEBEC_VERSION_ID, QUEBEC_DEFINITION_ID, QUEBEC_VERSION, SchemaStatus.DRAFT, QUEBEC_SCHEMA_JSON));
        activate(QUEBEC_VERSION_ID);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveSchema> findActives(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        return definitions.findByCode(code).filter(DocumentDefinition::isEnabled).map(this::activesOf).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public DocumentIaCatalogResponse catalog() {
        List<Schema> schemas = new ArrayList<>();
        for (DocumentDefinitionVersion version : versions.findByStatus(SchemaStatus.ACTIVE)) {
            definitions
                    .findById(version.getDefinitionId())
                    .filter(DocumentDefinition::isEnabled)
                    .ifPresent(definition -> schemas.add(toCatalog(definition, version)));
        }
        schemas.sort((left, right) -> {
            int byCode = left.code().compareTo(right.code());
            return byCode != 0 ? byCode : left.version().compareTo(right.version());
        });
        return new DocumentIaCatalogResponse(List.copyOf(schemas));
    }

    @Transactional
    public void activate(UUID versionId) {
        DocumentDefinitionVersion version = versions
                .findById(versionId)
                .orElseThrow(() -> ApiException.notFound("Schema version not found"));
        if (version.getStatus() != SchemaStatus.DRAFT) {
            throw ApiException.conflict("invalid_status", "Only a draft schema can be activated");
        }
        DocumentDefinition definition = definitions
                .findById(version.getDefinitionId())
                .orElseThrow(() -> ApiException.notFound("Schema definition not found"));
        JsonNode schema = readSchema(version.getSchemaJson());
        List<FieldSpec> fieldSpecs = validate(definition, version, schema);
        List<RuleSpec> ruleSpecs = rules(schema, fieldSpecs);

        Instant now = Instant.now();
        fields.deleteByVersionId(version.getId());
        rules.deleteByVersionId(version.getId());
        int order = 0;
        for (FieldSpec spec : fieldSpecs) {
            fields.save(new DocumentField(
                    UUID.randomUUID(),
                    version.getId(),
                    spec.name(),
                    spec.type(),
                    spec.required(),
                    order++,
                    spec.normalizer(),
                    spec.formats()));
        }
        for (RuleSpec spec : ruleSpecs) {
            rules.save(new DocumentValidationRule(
                    UUID.randomUUID(),
                    version.getId(),
                    spec.level(),
                    spec.code(),
                    spec.expression(),
                    spec.severity()));
        }
        version.activate(now);
        definition.touch(now);
    }

    private List<ActiveSchema> activesOf(DocumentDefinition definition) {
        return versions.findByDefinitionIdAndStatus(definition.getId(), SchemaStatus.ACTIVE).stream()
                .map(version -> toActive(definition, version))
                .toList();
    }

    private ActiveSchema toActive(DocumentDefinition definition, DocumentDefinitionVersion version) {
        Set<String> names = new HashSet<>();
        for (DocumentField field : fields.findByVersionIdOrderByFieldOrderAsc(version.getId())) {
            names.add(field.getName());
        }
        return new ActiveSchema(
                version.getId(),
                definition.getCode(),
                definition.getCountry(),
                definition.getDocumentType(),
                version.getVersion(),
                List.of(definition.getSide()),
                jurisdiction(version.getSchemaJson()),
                Set.copyOf(names));
    }

    private Schema toCatalog(DocumentDefinition definition, DocumentDefinitionVersion version) {
        return new Schema(
                version.getId(),
                definition.getCode(),
                definition.getCountry(),
                definition.getDocumentType(),
                definition.getSide(),
                version.getVersion(),
                jurisdiction(version.getSchemaJson()));
    }

    private String jurisdiction(String schemaJson) {
        JsonNode node = readSchema(schemaJson).get("issuingJurisdiction");
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText();
    }

    private JsonNode readSchema(String schemaJson) {
        try {
            JsonNode node = objectMapper.readTree(schemaJson);
            if (node == null || !node.isObject()) {
                throw activation("schema_json", "invalid");
            }
            return node;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw activation("schema_json", "invalid");
        }
    }

    private List<FieldSpec> validate(DocumentDefinition definition, DocumentDefinitionVersion version, JsonNode schema) {
        requireEqual(schema, "code", definition.getCode());
        requireEqual(schema, "country", definition.getCountry());
        requireEqual(schema, "documentType", definition.getDocumentType());
        requireEqual(schema, "side", definition.getSide());
        requireEqual(schema, "version", version.getVersion());
        requireEqual(schema, "mrzFormat", definition.getMrzFormat().name());
        JsonNode fieldNodes = schema.get("fields");
        if (fieldNodes == null || !fieldNodes.isArray() || fieldNodes.isEmpty()) {
            throw activation("fields", "required");
        }
        List<FieldSpec> specs = new ArrayList<>();
        Set<String> names = new HashSet<>();
        boolean mrzField = false;
        for (JsonNode field : fieldNodes) {
            if (!field.isObject()) {
                throw activation("fields", "invalid");
            }
            String name = text(field, "name");
            if (name == null || !names.add(name)) {
                throw activation("fields", "duplicate");
            }
            FieldValueType type = valueType(field.get("type"));
            if (type == null) {
                throw activation("type", "unknown");
            }
            String formats = formats(field.get("formats"));
            if (type == FieldValueType.DATE && (formats == null || formats.isBlank())) {
                throw activation("formats", "required");
            }
            if (type == FieldValueType.MRZ) {
                mrzField = true;
            }
            boolean required = field.path("required").asBoolean(false);
            specs.add(new FieldSpec(name, type, required, text(field, "normalizer"), formats));
        }
        if (definition.getMrzFormat() != MrzFormat.NONE && !mrzField) {
            throw activation("mrz", "required");
        }
        return List.copyOf(specs);
    }

    private List<RuleSpec> rules(JsonNode schema, List<FieldSpec> fields) {
        JsonNode nodes = schema.get("rules");
        if (nodes == null || nodes.isNull()) {
            return List.of();
        }
        if (!nodes.isArray()) {
            throw activation("rules", "invalid");
        }
        Set<String> dateFields = new HashSet<>();
        for (FieldSpec field : fields) {
            if (field.type() == FieldValueType.DATE) {
                dateFields.add(field.name());
            }
        }
        List<RuleSpec> specs = new ArrayList<>();
        for (JsonNode rule : nodes) {
            if (!rule.isObject()) {
                throw activation("rules", "invalid");
            }
            RuleLevel level = enumValue(RuleLevel.class, text(rule, "level"));
            RuleSeverity severity = enumValue(RuleSeverity.class, text(rule, "severity"));
            String code = text(rule, "code");
            String expression = text(rule, "expression");
            if (level == null || severity == null || code == null || expression == null) {
                throw activation("rules", "invalid");
            }
            if (!allowedExpression(expression, dateFields)) {
                throw activation("expression", "unknown");
            }
            specs.add(new RuleSpec(level, code, expression, severity));
        }
        return specs;
    }

    private static boolean allowedExpression(String expression, Set<String> dateFields) {
        var matcher = COMPARISON.matcher(expression.trim());
        if (!matcher.matches()) {
            return false;
        }
        return dateFields.contains(matcher.group(1)) && dateFields.contains(matcher.group(3));
    }

    private void requireEqual(JsonNode schema, String name, String expected) {
        String actual = text(schema, name);
        if (actual == null || !actual.equals(expected)) {
            throw activation(name, "mismatch");
        }
    }

    private static FieldValueType valueType(JsonNode node) {
        return enumValue(FieldValueType.class, node == null || !node.isTextual() ? null : node.asText());
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String formats(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                return null;
            }
            values.add(item.asText());
        }
        return String.join(",", values);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private static ApiException activation(String field, String code) {
        return ApiException.validation("Schema cannot be activated", List.of(new ErrorDetail(field, code)));
    }

    private record FieldSpec(String name, FieldValueType type, boolean required, String normalizer, String formats) {}

    private record RuleSpec(RuleLevel level, String code, String expression, RuleSeverity severity) {}
}
