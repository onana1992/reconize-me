package com.kyc.entities;

import com.kyc.enums.RuleLevel;
import com.kyc.enums.RuleSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_validation_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentValidationRule {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private RuleLevel level;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String expression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RuleSeverity severity;

    public DocumentValidationRule(
            UUID id,
            UUID versionId,
            RuleLevel level,
            String code,
            String expression,
            RuleSeverity severity) {
        this.id = id;
        this.versionId = versionId;
        this.level = level;
        this.code = code;
        this.expression = expression;
        this.severity = severity;
    }
}
