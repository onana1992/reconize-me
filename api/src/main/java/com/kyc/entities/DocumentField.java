package com.kyc.entities;

import com.kyc.enums.FieldValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "document_fields",
        uniqueConstraints = @UniqueConstraint(columnNames = {"version_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentField {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 32)
    private FieldValueType valueType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "field_order", nullable = false)
    private int fieldOrder;

    @Column(length = 32)
    private String normalizer;

    @Column(length = 255)
    private String formats;

    public DocumentField(
            UUID id,
            UUID versionId,
            String name,
            FieldValueType valueType,
            boolean required,
            int fieldOrder,
            String normalizer,
            String formats) {
        this.id = id;
        this.versionId = versionId;
        this.name = name;
        this.valueType = valueType;
        this.required = required;
        this.fieldOrder = fieldOrder;
        this.normalizer = normalizer;
        this.formats = formats;
    }
}
