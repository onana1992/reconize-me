package com.kyc.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class IdempotencyKeyId implements Serializable {

    private UUID organizationId;
    private String keyValue;

    public IdempotencyKeyId() {}

    public IdempotencyKeyId(UUID organizationId, String keyValue) {
        this.organizationId = organizationId;
        this.keyValue = keyValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyKeyId that)) {
            return false;
        }
        return Objects.equals(organizationId, that.organizationId) && Objects.equals(keyValue, that.keyValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationId, keyValue);
    }
}
