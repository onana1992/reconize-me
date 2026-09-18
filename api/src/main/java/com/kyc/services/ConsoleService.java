package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.ApiKeyListItem;
import com.kyc.dto.console.AuditListResponse;
import com.kyc.dto.console.MeResponse;
import com.kyc.dto.console.TeamResponse;
import com.kyc.entities.ApiKey;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Membership;
import com.kyc.entities.MembershipId;
import com.kyc.entities.Organization;
import com.kyc.entities.User;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.MembershipInviteRepository;
import com.kyc.repositories.MembershipRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.UserRepository;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.ConsoleRole;
import com.kyc.security.Permission;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsoleService {

    private static final int AUDIT_DEFAULT_LIMIT = 20;
    private static final int AUDIT_MAX_LIMIT = 100;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipInviteRepository membershipInviteRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditEventRepository auditEventRepository;
    private final IntegrationService integrationService;
    private final AccountService accountService;
    private final SessionService sessionService;
    private final CreditService creditService;
    private final ObjectMapper objectMapper;

    public ConsoleService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            MembershipInviteRepository membershipInviteRepository,
            ApiKeyRepository apiKeyRepository,
            AuditEventRepository auditEventRepository,
            IntegrationService integrationService,
            AccountService accountService,
            SessionService sessionService,
            CreditService creditService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.membershipInviteRepository = membershipInviteRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.auditEventRepository = auditEventRepository;
        this.integrationService = integrationService;
        this.accountService = accountService;
        this.sessionService = sessionService;
        this.creditService = creditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MeResponse me(ConsolePrincipal principal) {
        User user = userRepository.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User not found"));
        Organization organization = organizationRepository
                .findById(principal.organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
        var billing = creditService.summary(principal.organizationId());
        var usage = billing.usage().isEmpty()
                ? new com.kyc.dto.billing.BillingResponse.ProductUsage("identity", 0, 0, 0)
                : billing.usage().get(0);
        return new MeResponse(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                new MeResponse.OrganizationMe(organization.getId(), organization.getName(), organization.getSlug()),
                principal.role(),
                ConsoleAuth.permissionNames(principal.role()),
                billing.currency(),
                billing.balanceMinor(),
                usage.sandboxCount(),
                usage.liveCount(),
                billing.liveUnlocked());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyListItem> listKeys(ConsolePrincipal principal) {
        ConsoleAuth.require(principal, Permission.API_KEY_READ);
        return apiKeyRepository.findByOrganizationIdOrderByCreatedAtDesc(principal.organizationId()).stream()
                .map(key -> new ApiKeyListItem(
                        key.getId(), key.getIntegrationId(), key.getKeyPrefix(), key.getCreatedAt(), key.isRevoked()))
                .toList();
    }

    @Transactional
    public IssuedApiKeyResponse createKey(ConsolePrincipal principal) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        return integrationService.issueTestKey(principal.organizationId(), principal.userId());
    }

    @Transactional
    public void revokeKey(ConsolePrincipal principal, UUID keyId) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        ApiKey key = apiKeyRepository
                .findById(keyId)
                .filter(row -> row.getOrganizationId().equals(principal.organizationId()))
                .orElseThrow(() -> ApiException.notFound("API key not found"));
        key.revoke();
        audit(
                principal,
                "api_key.revoked",
                "api_key",
                keyId,
                "{}",
                Instant.now());
    }

    @Transactional(readOnly = true)
    public TeamResponse team(ConsolePrincipal principal) {
        ConsoleAuth.require(principal, Permission.TEAM_READ);
        Instant now = Instant.now();
        List<Membership> memberships =
                membershipRepository.findByOrganizationIdOrderByCreatedAtAsc(principal.organizationId());
        Map<UUID, User> users = userRepository
                .findAllById(memberships.stream().map(Membership::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<TeamResponse.MemberItem> members = new ArrayList<>();
        for (Membership membership : memberships) {
            User user = users.get(membership.getUserId());
            if (user == null) {
                continue;
            }
            members.add(new TeamResponse.MemberItem(
                    user.getId(),
                    user.getEmail(),
                    membership.getRole(),
                    membership.getStatus(),
                    membership.getCreatedAt()));
        }
        List<TeamResponse.InviteItem> invites = membershipInviteRepository
                .findByOrganizationIdAndAcceptedAtIsNullAndCancelledAtIsNullOrderByCreatedAtAsc(
                        principal.organizationId())
                .stream()
                .filter(invite -> invite.pending(now))
                .map(invite -> new TeamResponse.InviteItem(
                        invite.getId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt()))
                .toList();
        return new TeamResponse(members, invites);
    }

    @Transactional
    public void invite(ConsolePrincipal principal, String email, String role) {
        ConsoleAuth.require(principal, Permission.TEAM_WRITE);
        accountService.createInvite(principal.organizationId(), principal.userId(), email, role);
    }

    @Transactional
    public void resendInvite(ConsolePrincipal principal, UUID inviteId) {
        ConsoleAuth.require(principal, Permission.TEAM_WRITE);
        accountService.resendInvite(principal.organizationId(), principal.userId(), inviteId);
    }

    @Transactional
    public void cancelInvite(ConsolePrincipal principal, UUID inviteId) {
        ConsoleAuth.require(principal, Permission.TEAM_WRITE);
        accountService.cancelInvite(principal.organizationId(), principal.userId(), inviteId);
    }

    @Transactional
    public void removeMember(ConsolePrincipal principal, UUID userId) {
        ConsoleAuth.require(principal, Permission.TEAM_WRITE);
        guardNotSelf(principal, userId, "cannot_remove_self", "You cannot remove yourself from the organization");
        Membership membership = requireMember(principal.organizationId(), userId);
        if (membership.isOwner()) {
            ConsoleAuth.require(principal, Permission.OWNERSHIP);
        }
        guardLastActiveOwner(membership);
        membershipRepository.delete(membership);
        sessionService.invalidateUser(userId);
        audit(principal, "membership.removed", "user", userId, "{}", Instant.now());
    }

    @Transactional
    public void changeRole(ConsolePrincipal principal, UUID userId, String rawRole) {
        ConsoleAuth.require(principal, Permission.TEAM_WRITE);
        guardNotSelf(principal, userId, "cannot_change_own_role", "You cannot change your own role");
        ConsoleRole next = ConsoleRole.parse(rawRole);
        Membership membership = requireMember(principal.organizationId(), userId);
        if (membership.isOwner() || next == ConsoleRole.OWNER) {
            ConsoleAuth.require(principal, Permission.OWNERSHIP);
        }
        if (membership.getRole().equals(next.value())) {
            return;
        }
        if (membership.isOwner() && next != ConsoleRole.OWNER) {
            guardLastActiveOwner(membership);
        }
        String from = membership.getRole();
        membership.setRole(next.value());
        audit(
                principal,
                "membership.role_changed",
                "user",
                userId,
                roleChangedPayload(from, next.value()),
                Instant.now());
    }

    @Transactional
    public void transfer(ConsolePrincipal principal, UUID userId) {
        ConsoleAuth.require(principal, Permission.OWNERSHIP);
        if (principal.userId().equals(userId)) {
            throw ApiException.conflict("cannot_transfer_self", "Cannot transfer ownership to yourself");
        }
        Membership target = requireMember(principal.organizationId(), userId);
        if (!target.isActive()) {
            throw ApiException.conflict("membership_disabled", "Cannot transfer ownership to a disabled member");
        }
        Membership actor = requireMember(principal.organizationId(), principal.userId());
        Instant now = Instant.now();
        String targetFrom = target.getRole();
        String actorFrom = actor.getRole();
        target.setRole(ConsoleRole.OWNER.value());
        actor.setRole(ConsoleRole.ADMIN.value());
        if (!targetFrom.equals(ConsoleRole.OWNER.value())) {
            audit(
                    principal,
                    "membership.role_changed",
                    "user",
                    target.getUserId(),
                    roleChangedPayload(targetFrom, ConsoleRole.OWNER.value()),
                    now);
        }
        if (!actorFrom.equals(ConsoleRole.ADMIN.value())) {
            audit(
                    principal,
                    "membership.role_changed",
                    "user",
                    actor.getUserId(),
                    roleChangedPayload(actorFrom, ConsoleRole.ADMIN.value()),
                    now);
        }
    }

    @Transactional
    public void disableMember(ConsolePrincipal principal, UUID userId) {
        ConsoleAuth.require(principal, Permission.TEAM_WRITE);
        if (principal.userId().equals(userId)) {
            throw ApiException.conflict("cannot_disable_self", "You cannot disable your own membership");
        }
        Membership membership = requireMember(principal.organizationId(), userId);
        if (membership.isOwner()) {
            ConsoleAuth.require(principal, Permission.OWNERSHIP);
        }
        if (!membership.isActive()) {
            return;
        }
        guardLastActiveOwner(membership);
        Instant now = Instant.now();
        membership.disable(now, principal.userId());
        sessionService.invalidateUser(userId);
        audit(principal, "membership.disabled", "user", userId, "{}", now);
    }

    @Transactional
    public void enableMember(ConsolePrincipal principal, UUID userId) {
        ConsoleAuth.require(principal, Permission.TEAM_WRITE);
        Membership membership = requireMember(principal.organizationId(), userId);
        if (membership.isOwner()) {
            ConsoleAuth.require(principal, Permission.OWNERSHIP);
        }
        if (membership.isActive()) {
            return;
        }
        membership.enable();
        audit(principal, "membership.enabled", "user", userId, "{}", Instant.now());
    }

    @Transactional(readOnly = true)
    public AuditListResponse audit(ConsolePrincipal principal, String action, String cursor, Integer limit) {
        ConsoleAuth.require(principal, Permission.AUDIT_READ);
        int size = limit == null ? AUDIT_DEFAULT_LIMIT : Math.min(AUDIT_MAX_LIMIT, Math.max(1, limit));
        Long before = parseCursor(cursor);
        Pageable page = PageRequest.of(0, size + 1);
        UUID orgId = principal.organizationId();
        boolean filtered = action != null && !action.isBlank();
        List<AuditEvent> rows;
        if (filtered && before != null) {
            rows = auditEventRepository.findByOrganizationIdAndActionAndIdLessThanOrderByIdDesc(
                    orgId, action, before, page);
        } else if (filtered) {
            rows = auditEventRepository.findByOrganizationIdAndActionOrderByIdDesc(orgId, action, page);
        } else if (before != null) {
            rows = auditEventRepository.findByOrganizationIdAndIdLessThanOrderByIdDesc(orgId, before, page);
        } else {
            rows = auditEventRepository.findByOrganizationIdOrderByIdDesc(orgId, page);
        }
        boolean more = rows.size() > size;
        List<AuditEvent> pageRows = more ? rows.subList(0, size) : rows;
        String next = more && !pageRows.isEmpty() ? String.valueOf(pageRows.get(pageRows.size() - 1).getId()) : null;
        List<AuditListResponse.AuditEventItem> events = pageRows.stream()
                .map(event -> new AuditListResponse.AuditEventItem(
                        event.getId(),
                        event.getAction(),
                        event.getActorType(),
                        event.getActorId(),
                        event.getResourceType(),
                        event.getResourceId(),
                        payloadNode(event.getPayload()),
                        event.getIpAddress(),
                        event.getCreatedAt()))
                .toList();
        return new AuditListResponse(events, next);
    }

    private Membership requireMember(UUID organizationId, UUID userId) {
        return membershipRepository
                .findById(new MembershipId(userId, organizationId))
                .orElseThrow(() -> ApiException.notFound("Member not found"));
    }

    private static void guardNotSelf(ConsolePrincipal principal, UUID userId, String code, String message) {
        if (principal.userId().equals(userId)) {
            throw ApiException.conflict(code, message);
        }
    }

    private void guardLastActiveOwner(Membership membership) {
        if (!membership.isOwner() || !membership.isActive()) {
            return;
        }
        if (membershipRepository.countByOrganizationIdAndRoleAndStatus(
                        membership.getOrganizationId(), Membership.ROLE_OWNER, Membership.STATUS_ACTIVE)
                <= 1) {
            throw ApiException.conflict("last_owner", "The organization must keep at least one owner");
        }
    }

    private void audit(
            ConsolePrincipal principal,
            String action,
            String resourceType,
            UUID resourceId,
            String payload,
            Instant now) {
        auditEventRepository.save(new AuditEvent(
                principal.organizationId(),
                "user",
                principal.userId(),
                action,
                resourceType,
                resourceId,
                payload,
                now));
    }

    private static String roleChangedPayload(String from, String to) {
        return "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}";
    }

    private JsonNode payloadNode(String payload) {
        try {
            return objectMapper.readTree(payload == null || payload.isBlank() ? "{}" : payload);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException ex) {
            throw ApiException.validation("Invalid cursor", List.of(new ErrorDetail("cursor", "invalid")));
        }
    }
}
