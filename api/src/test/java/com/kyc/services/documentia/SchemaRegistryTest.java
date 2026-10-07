package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kyc.entities.DocumentDefinition;
import com.kyc.entities.DocumentDefinitionVersion;
import com.kyc.enums.MrzFormat;
import com.kyc.enums.SchemaStatus;
import com.kyc.repositories.DocumentDefinitionRepository;
import com.kyc.repositories.DocumentDefinitionVersionRepository;
import com.kyc.repositories.DocumentFieldRepository;
import com.kyc.repositories.DocumentValidationRuleRepository;
import com.kyc.web.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SchemaRegistryTest {

    @Autowired
    private SchemaRegistry registry;

    @Autowired
    private DocumentDefinitionRepository definitions;

    @Autowired
    private DocumentDefinitionVersionRepository versions;

    @Autowired
    private DocumentFieldRepository fields;

    @Autowired
    private DocumentValidationRuleRepository rules;

    @Test
    void dateWithoutFormatStaysDraft() {
        UUID versionId = draft("DATE_DRAFT", MrzFormat.NONE, dateFieldWithoutFormats());
        assertThrows(ApiException.class, () -> registry.activate(versionId));
        assertEquals(SchemaStatus.DRAFT, versions.findById(versionId).orElseThrow().getStatus());
    }

    @Test
    void unknownExpressionStaysDraft() {
        UUID versionId = draft("EXPR_DRAFT", MrzFormat.NONE, unknownExpression());
        assertThrows(ApiException.class, () -> registry.activate(versionId));
        assertEquals(SchemaStatus.DRAFT, versions.findById(versionId).orElseThrow().getStatus());
    }

    @Test
    void activationKeepsPreviousEditionInCirculation() {
        UUID first = draft("RETIRE_ME", MrzFormat.NONE, validSchema("RETIRE_ME"));
        registry.activate(first);
        UUID second = anotherVersion(first, "2026");
        registry.activate(second);
        assertEquals(SchemaStatus.ACTIVE, versions.findById(first).orElseThrow().getStatus());
        assertEquals(SchemaStatus.ACTIVE, versions.findById(second).orElseThrow().getStatus());
        assertEquals(2, registry.findActives("RETIRE_ME").size());
        assertEquals(2, fields.findByVersionIdOrderByFieldOrderAsc(first).size());
        assertEquals(2, fields.findByVersionIdOrderByFieldOrderAsc(second).size());
        assertEquals(1, rules.findByVersionId(second).size());
        assertEquals(
                2,
                registry.catalog().schemas().stream()
                        .filter(schema -> "RETIRE_ME".equals(schema.code()))
                        .count());
    }

    private UUID draft(String code, MrzFormat mrzFormat, String schemaJson) {
        UUID definitionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        definitions.save(new DocumentDefinition(
                definitionId, code, "CM", "NATIONAL_ID", "FRONT", mrzFormat, true, Instant.now()));
        versions.save(new DocumentDefinitionVersion(versionId, definitionId, "2025", SchemaStatus.DRAFT, schemaJson));
        return versionId;
    }

    private UUID anotherVersion(UUID activeVersionId, String version) {
        UUID definitionId = versions.findById(activeVersionId).orElseThrow().getDefinitionId();
        UUID versionId = UUID.randomUUID();
        versions.save(new DocumentDefinitionVersion(
                versionId, definitionId, version, SchemaStatus.DRAFT, validSchema("RETIRE_ME").replace("2025", version)));
        return versionId;
    }

    private static String validSchema(String code) {
        return """
                {
                  "code": "%s",
                  "country": "CM",
                  "documentType": "NATIONAL_ID",
                  "version": "2025",
                  "side": "FRONT",
                  "mrzFormat": "NONE",
                  "fields": [
                    {"name": "dateOfBirth", "type": "DATE", "required": true, "formats": ["yyyy-MM-dd"]},
                    {"name": "dateOfIssue", "type": "DATE", "required": true, "formats": ["yyyy-MM-dd"]}
                  ],
                  "rules": [
                    {"level": "L3", "code": "DOB_AFTER_ISSUE", "expression": "dateOfBirth < dateOfIssue", "severity": "ERROR"}
                  ]
                }
                """
                .formatted(code);
    }

    private static String dateFieldWithoutFormats() {
        return """
                {
                  "code": "DATE_DRAFT",
                  "country": "CM",
                  "documentType": "NATIONAL_ID",
                  "version": "2025",
                  "side": "FRONT",
                  "mrzFormat": "NONE",
                  "fields": [
                    {"name": "dateOfBirth", "type": "DATE", "required": true}
                  ]
                }
                """;
    }

    private static String unknownExpression() {
        return """
                {
                  "code": "EXPR_DRAFT",
                  "country": "CM",
                  "documentType": "NATIONAL_ID",
                  "version": "2025",
                  "side": "FRONT",
                  "mrzFormat": "NONE",
                  "fields": [
                    {"name": "dateOfBirth", "type": "DATE", "required": true, "formats": ["yyyy-MM-dd"]}
                  ],
                  "rules": [
                    {"level": "L3", "code": "NOPE", "expression": "lastName == dateOfBirth", "severity": "ERROR"}
                  ]
                }
                """;
    }
}
