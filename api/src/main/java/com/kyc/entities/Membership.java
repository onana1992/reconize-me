package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "memberships")
@IdClass(MembershipId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_MEMBER = "member";

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Membership(UUID userId, UUID organizationId, String role, Instant createdAt) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public boolean isOwner() {
        return ROLE_OWNER.equals(role);
    }
}
