package com.kyc.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class MembershipId implements Serializable {

    private UUID userId;
    private UUID organizationId;

    public MembershipId() {}

    public MembershipId(UUID userId, UUID organizationId) {
        this.userId = userId;
        this.organizationId = organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MembershipId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(organizationId, that.organizationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, organizationId);
    }
}
