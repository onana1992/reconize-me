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
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "disabled_by_user_id")
    private UUID disabledByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Membership(UUID userId, UUID organizationId, String role, Instant createdAt) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
        this.status = STATUS_ACTIVE;
        this.createdAt = createdAt;
    }

    public boolean isOwner() {
        return ROLE_OWNER.equals(role);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void disable(Instant at, UUID byUserId) {
        this.status = STATUS_DISABLED;
        this.disabledAt = at;
        this.disabledByUserId = byUserId;
    }

    public void enable() {
        this.status = STATUS_ACTIVE;
        this.disabledAt = null;
        this.disabledByUserId = null;
    }
}
