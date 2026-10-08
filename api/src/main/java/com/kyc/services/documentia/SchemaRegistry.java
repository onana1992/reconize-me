package com.kyc.services.documentia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.documentia.DocumentIaCatalogResponse;
import com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaRegistry implements SchemaCatalog {

    public static final UUID QUEBEC_DEFINITION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000a1");
    public static final UUID QUEBEC_VERSION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000a2");
    public static final UUID QUEBEC_BACK_DEFINITION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000c1");
    public static final UUID QUEBEC_BACK_VERSION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000c2");
    public static final String QUEBEC_CODE = "QUEBEC_DRIVER_LICENSE";
    public static final String QUEBEC_VERSION = "2024";
    public static final UUID PASSPORT_DEFINITION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000b1");
    public static final UUID PASSPORT_VERSION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000b2");
    public static final String CANADA_PR_CODE = "CANADA_PERMANENT_RESIDENT";
    public static final String CANADA_PR_VERSION = "2015";
    public static final UUID CANADA_PR_DEFINITION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000e1");
    public static final UUID CANADA_PR_VERSION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000e2");
    public static final UUID CANADA_PR_BACK_DEFINITION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000e3");
    public static final UUID CANADA_PR_BACK_VERSION_ID = UUID.fromString("018f5a00-0000-7000-8000-0000000000e4");
    public static final String PASSPORT_CODE = "PASSPORT_TD3";

    static final String PASSPORT_SCHEMA_JSON =
            """
            {
              "code": "PASSPORT_TD3",
              "country": "UT",
              "documentType": "PASSPORT",
              "version": "2024",
              "side": "FRONT",
              "mrzFormat": "TD3",
              "fields": [
                {"name": "lastName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME"},
                {"name": "firstName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME"},
                {"name": "documentNumber", "type": "DOCUMENT_NUMBER", "required": true},
                {"name": "dateOfBirth", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd"]},
                {"name": "expirationDate", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd"]},
                {"name": "sex", "type": "SEX", "required": true},
                {"name": "nationality", "type": "NATIONALITY", "required": false},
                {"name": "mrz", "type": "MRZ", "required": true}
              ],
              "rules": []
            }
            """;

    static final String CANADA_PR_SCHEMA_JSON =
            """
            {
              "code": "CANADA_PERMANENT_RESIDENT",
              "country": "CA",
              "documentType": "RESIDENCE_PERMIT",
              "version": "2015",
              "side": "FRONT",
              "mrzFormat": "NONE",
              "fields": [
                {"name": "lastName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME", "hint": "surname on the Name/Nom line; it may contain two words"},
                {"name": "firstName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME", "hint": "given names on the line under the surname"},
                {"name": "documentNumber", "type": "DOCUMENT_NUMBER", "required": true, "hint": "labeled ID No; this is not the PD number on the back"},
                {"name": "dateOfBirth", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd"], "hint": "labeled Date of birth; the card prints a bilingual date such as 09 APR /AVR 92; write yyyy-MM-dd, and a past year 92 is 1992"},
                {"name": "expirationDate", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd"], "hint": "labeled Expiry; write yyyy-MM-dd, and a year 30 is 2030"},
                {"name": "sex", "type": "SEX", "required": false, "hint": "labeled Sex"},
                {"name": "nationality", "type": "NATIONALITY", "required": false, "hint": "labeled Nationality, alpha-3 such as CMR"}
              ],
              "rules": []
            }
            """;

    static final String CANADA_PR_BACK_SCHEMA_JSON =
            """
            {
              "code": "CANADA_PERMANENT_RESIDENT",
              "country": "CA",
              "documentType": "RESIDENCE_PERMIT",
              "version": "2015",
              "side": "BACK",
              "mrzFormat": "TD1",
              "fields": [
                {"name": "documentNumber", "type": "DOCUMENT_NUMBER", "required": true, "hint": "the PD number printed above the machine-readable zone, such as PD4077908; do not use the front ID No"},
                {"name": "lastName", "type": "NAME", "required": false, "normalizer": "ICAO_NAME", "hint": "surname from the third MRZ line, before the double filler"},
                {"name": "firstName", "type": "NAME", "required": false, "normalizer": "ICAO_NAME", "hint": "given names from the third MRZ line, after the double filler"},
                {"name": "dateOfBirth", "type": "DATE", "required": false, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd"], "hint": "birth date from the MRZ; write yyyy-MM-dd"},
                {"name": "expirationDate", "type": "DATE", "required": false, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd"], "hint": "expiry date from the MRZ; write yyyy-MM-dd, and a year 30 is 2030"},
                {"name": "sex", "type": "SEX", "required": false, "hint": "sex letter from the MRZ"},
                {"name": "nationality", "type": "NATIONALITY", "required": false, "hint": "nationality alpha-3 from the MRZ, such as CMR"},
                {"name": "mrz", "type": "MRZ", "required": true, "hint": "the three lines of 30 characters at the bottom of the back"},
                {"name": "placeOfLanding", "type": "STRING", "required": false, "hint": "labeled Place of landing"},
                {"name": "residentSince", "type": "DATE", "required": false, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd"], "hint": "labeled PR Since; write yyyy-MM-dd"},
                {"name": "eyeColor", "type": "STRING", "required": false, "hint": "labeled Eyes"},
                {"name": "heightCm", "type": "STRING", "required": false, "hint": "labeled Height, digits only"},
                {"name": "countryOfBirth", "type": "NATIONALITY", "required": false, "hint": "labeled COB, alpha-3 such as CMR"}
              ],
              "rules": []
            }
            """;

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
                {"name": "firstName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME", "hint": "given names on the line under the surname"},
                {"name": "lastName", "type": "NAME", "required": true, "normalizer": "ICAO_NAME", "hint": "surname on the line directly under the document number; it may contain two words"},
                {"name": "dateOfBirth", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy"], "hint": "date labeled Date de naissance"},
                {"name": "expirationDate", "type": "DATE", "required": true, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy"], "hint": "date labeled Expire le"},
                {"name": "documentNumber", "type": "DOCUMENT_NUMBER", "required": true, "hint": "large number at the top of the card"},
                {"name": "dateOfIssue", "type": "DATE", "required": false, "normalizer": "ISO_DATE", "formats": ["yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy"], "hint": "date labeled Valide le"},
                {"name": "sex", "type": "SEX", "required": false, "hint": "labeled Sexe"},
                {"name": "address", "type": "ADDRESS", "required": false, "hint": "address lines under the date of birth"},
                {"name": "licenseClass", "type": "STRING", "required": false, "hint": "labeled Classe(s)"},
                {"name": "conditions", "type": "STRING", "required": false, "hint": "labeled Cond."},
                {"name": "mentions", "type": "STRING", "required": false, "hint": "labeled Mention(s)"},
                {"name": "referenceNumber", "type": "STRING", "required": false, "hint": "labeled Numero de reference"},
                {"name": "heightCm", "type": "STRING", "required": false, "hint": "labeled Taille (cm)"},
                {"name": "eyeColor", "type": "STRING", "required": false, "hint": "labeled Yeux"}
              ],
              "rules": [
                {"level": "L3", "code": "DOB_AFTER_ISSUE", "expression": "dateOfBirth > dateOfIssue", "severity": "ERROR"}
              ]
            }
            """;

    static final String QUEBEC_BACK_SCHEMA_JSON =
            """
            {
              "code": "QUEBEC_DRIVER_LICENSE",
              "country": "CA",
              "documentType": "DRIVING_LICENSE",
              "version": "2024",
              "side": "BACK",
              "mrzFormat": "NONE",
              "issuingJurisdiction": "QC",
              "fields": [
                {"name": "barcode", "type": "STRING", "required": true, "hint": "PDF417 barcode on the back; value PRESENT when that barcode is visible"},
                {"name": "classDescription", "type": "STRING", "required": false, "hint": "sentence printed after CLASSE(S):"},
                {"name": "cardEdition", "type": "STRING", "required": false, "hint": "edition in parentheses at the lower right, such as 2024-01"}
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
        if (definitions.findByCodeAndSide(QUEBEC_CODE, "FRONT").isPresent()) {
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

    @Transactional
    public void ensureQuebecLicenseBack() {
        if (definitions.findByCodeAndSide(QUEBEC_CODE, "BACK").isPresent()) {
            return;
        }
        Instant now = Instant.parse("2026-10-07T20:00:00Z");
        definitions.save(new DocumentDefinition(
                QUEBEC_BACK_DEFINITION_ID,
                QUEBEC_CODE,
                "CA",
                "DRIVING_LICENSE",
                "BACK",
                MrzFormat.NONE,
                true,
                now));
        versions.save(new DocumentDefinitionVersion(
                QUEBEC_BACK_VERSION_ID,
                QUEBEC_BACK_DEFINITION_ID,
                QUEBEC_VERSION,
                SchemaStatus.DRAFT,
                QUEBEC_BACK_SCHEMA_JSON));
        activate(QUEBEC_BACK_VERSION_ID);
    }

    @Transactional
    public void ensurePassportTd3() {
        if (definitions.findByCodeAndSide(PASSPORT_CODE, "FRONT").isPresent()) {
            return;
        }
        Instant now = Instant.parse("2026-10-07T16:00:00Z");
        definitions.save(new DocumentDefinition(
                PASSPORT_DEFINITION_ID,
                PASSPORT_CODE,
                "UT",
                "PASSPORT",
                "FRONT",
                MrzFormat.TD3,
                true,
                now));
        versions.save(new DocumentDefinitionVersion(
                PASSPORT_VERSION_ID, PASSPORT_DEFINITION_ID, "2024", SchemaStatus.DRAFT, PASSPORT_SCHEMA_JSON));
        activate(PASSPORT_VERSION_ID);
    }

    @Transactional
    public void ensureCanadaPermanentResident() {
        if (definitions.findByCodeAndSide(CANADA_PR_CODE, "FRONT").isPresent()) {
            return;
        }
        Instant now = Instant.parse("2026-10-08T04:10:00Z");
        definitions.save(new DocumentDefinition(
                CANADA_PR_DEFINITION_ID,
                CANADA_PR_CODE,
                "CA",
                "RESIDENCE_PERMIT",
                "FRONT",
                MrzFormat.NONE,
                true,
                now));
        versions.save(new DocumentDefinitionVersion(
                CANADA_PR_VERSION_ID,
                CANADA_PR_DEFINITION_ID,
                CANADA_PR_VERSION,
                SchemaStatus.DRAFT,
                CANADA_PR_SCHEMA_JSON));
        activate(CANADA_PR_VERSION_ID);
    }

    @Transactional
    public void ensureCanadaPermanentResidentBack() {
        if (definitions.findByCodeAndSide(CANADA_PR_CODE, "BACK").isPresent()) {
            return;
        }
        Instant now = Instant.parse("2026-10-08T04:10:00Z");
        definitions.save(new DocumentDefinition(
                CANADA_PR_BACK_DEFINITION_ID,
                CANADA_PR_CODE,
                "CA",
                "RESIDENCE_PERMIT",
                "BACK",
                MrzFormat.TD1,
                true,
                now));
        versions.save(new DocumentDefinitionVersion(
                CANADA_PR_BACK_VERSION_ID,
                CANADA_PR_BACK_DEFINITION_ID,
                CANADA_PR_VERSION,
                SchemaStatus.DRAFT,
                CANADA_PR_BACK_SCHEMA_JSON));
        activate(CANADA_PR_BACK_VERSION_ID);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveSchema> findActives(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        List<ActiveSchema> schemas = new ArrayList<>();
        for (DocumentDefinition definition : definitions.findAllByCode(code)) {
            if (definition.isEnabled()) {
                schemas.addAll(activesOf(definition));
            }
        }
        return List.copyOf(schemas);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSide(String code, String side) {
        if (code == null || side == null) {
            return false;
        }
        return findActives(code).stream().anyMatch(schema -> schema.sides().contains(side));
    }

    @Transactional(readOnly = true)
    public Optional<SchemaEdition> edition(UUID versionId) {
        if (versionId == null) {
            return Optional.empty();
        }
        return versions.findById(versionId).flatMap(version -> definitions
                .findById(version.getDefinitionId())
                .map(definition -> toEdition(definition, version)));
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
            if (byCode != 0) {
                return byCode;
            }
            int bySide = left.side().compareTo(right.side());
            return bySide != 0 ? bySide : left.version().compareTo(right.version());
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

    private SchemaEdition toEdition(DocumentDefinition definition, DocumentDefinitionVersion version) {
        List<SchemaEdition.FieldDef> fieldDefs = new ArrayList<>();
        for (DocumentField field : fields.findByVersionIdOrderByFieldOrderAsc(version.getId())) {
            fieldDefs.add(new SchemaEdition.FieldDef(
                    field.getName(),
                    field.getValueType(),
                    field.isRequired(),
                    field.getNormalizer(),
                    field.getFormats()));
        }
        List<SchemaEdition.RuleDef> ruleDefs = new ArrayList<>();
        for (DocumentValidationRule rule : rules.findByVersionId(version.getId())) {
            ruleDefs.add(new SchemaEdition.RuleDef(
                    rule.getLevel(), rule.getCode(), rule.getExpression(), rule.getSeverity()));
        }
        return new SchemaEdition(
                version.getId(),
                definition.getCode(),
                definition.getCountry(),
                definition.getDocumentType(),
                version.getVersion(),
                definition.getSide(),
                jurisdiction(version.getSchemaJson()),
                List.copyOf(fieldDefs),
                List.copyOf(ruleDefs),
                definition.getMrzFormat());
    }

    private Schema toCatalog(DocumentDefinition definition, DocumentDefinitionVersion version) {
        return new Schema(
                version.getId(),
                definition.getCode(),
                definition.getCountry(),
                definition.getDocumentType(),
                definition.getSide(),
                version.getVersion(),
                jurisdiction(version.getSchemaJson()),
                fieldPrompts(version.getSchemaJson()));
    }

    private List<FieldPrompt> fieldPrompts(String schemaJson) {
        JsonNode nodes = readSchema(schemaJson).get("fields");
        if (nodes == null || !nodes.isArray()) {
            return List.of();
        }
        List<FieldPrompt> prompts = new ArrayList<>();
        for (JsonNode field : nodes) {
            String name = text(field, "name");
            if (name == null) {
                continue;
            }
            prompts.add(new FieldPrompt(name, field.path("required").asBoolean(false), text(field, "hint")));
        }
        return List.copyOf(prompts);
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
