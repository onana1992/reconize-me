package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idv_policy_documents")
@IdClass(IdvPolicyDocumentKey.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdvPolicyDocument {

    @Id
    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Id
    @Column(name = "definition_code", nullable = false, length = 64)
    private String definitionCode;

    public IdvPolicyDocument(UUID policyId, String definitionCode) {
        this.policyId = policyId;
        this.definitionCode = definitionCode;
    }
}
