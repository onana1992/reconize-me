package com.kyc.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class IdvPolicyDocumentKey implements Serializable {

    private UUID policyId;
    private String definitionCode;

    public IdvPolicyDocumentKey() {}

    public IdvPolicyDocumentKey(UUID policyId, String definitionCode) {
        this.policyId = policyId;
        this.definitionCode = definitionCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdvPolicyDocumentKey key)) {
            return false;
        }
        return Objects.equals(policyId, key.policyId) && Objects.equals(definitionCode, key.definitionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId, definitionCode);
    }
}
