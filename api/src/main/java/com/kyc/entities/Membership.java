package com.kyc.entities;

import com.kyc.enums.MembershipStatus;
import com.kyc.security.ConsoleRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipStatus status;

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
        this.status = MembershipStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public boolean isOwner() {
        return ConsoleRole.OWNER.matches(role);
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void disable(Instant at, UUID byUserId) {
        this.status = MembershipStatus.DISABLED;
        this.disabledAt = at;
        this.disabledByUserId = byUserId;
    }

    public void enable() {
        this.status = MembershipStatus.ACTIVE;
        this.disabledAt = null;
        this.disabledByUserId = null;
    }
}
