package com.kyc.services;

import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.ApiKeyListItem;
import com.kyc.dto.console.MeResponse;
import com.kyc.dto.console.TeamResponse;
import com.kyc.entities.ApiKey;
import com.kyc.entities.Membership;
import com.kyc.entities.Organization;
import com.kyc.entities.User;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.MembershipInviteRepository;
import com.kyc.repositories.MembershipRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.UserRepository;
import com.kyc.security.ConsolePrincipal;
import com.kyc.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsoleService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipInviteRepository membershipInviteRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyIssuer apiKeyIssuer;
    private final AccountService accountService;

    public ConsoleService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            MembershipInviteRepository membershipInviteRepository,
            ApiKeyRepository apiKeyRepository,
            ApiKeyIssuer apiKeyIssuer,
            AccountService accountService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.membershipInviteRepository = membershipInviteRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyIssuer = apiKeyIssuer;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public MeResponse me(ConsolePrincipal principal) {
        User user = userRepository.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User not found"));
        Organization organization = organizationRepository
                .findById(principal.organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
        return new MeResponse(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                new MeResponse.OrganizationMe(organization.getId(), organization.getName(), organization.getSlug(), "sandbox"),
                principal.role());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyListItem> listKeys(ConsolePrincipal principal) {
        return apiKeyRepository.findByOrganizationIdOrderByCreatedAtDesc(principal.organizationId()).stream()
                .map(key -> new ApiKeyListItem(key.getId(), key.getKeyPrefix(), key.getCreatedAt(), key.isRevoked()))
                .toList();
    }

    @Transactional
    public IssuedApiKeyResponse createKey(ConsolePrincipal principal) {
        requireOwner(principal);
        return apiKeyIssuer.issue(principal.organizationId(), principal.userId());
    }

    @Transactional
    public void revokeKey(ConsolePrincipal principal, UUID keyId) {
        requireOwner(principal);
        ApiKey key = apiKeyRepository
                .findById(keyId)
                .filter(row -> row.getOrganizationId().equals(principal.organizationId()))
                .orElseThrow(() -> ApiException.notFound("API key not found"));
        key.revoke();
    }

    @Transactional(readOnly = true)
    public TeamResponse team(ConsolePrincipal principal) {
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
                    user.getId(), user.getEmail(), membership.getRole(), membership.getCreatedAt()));
        }
        List<TeamResponse.InviteItem> invites = membershipInviteRepository
                .findByOrganizationIdAndAcceptedAtIsNullOrderByCreatedAtAsc(principal.organizationId())
                .stream()
                .filter(invite -> invite.pending(now))
                .map(invite -> new TeamResponse.InviteItem(
                        invite.getId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt()))
                .toList();
        return new TeamResponse(members, invites);
    }

    @Transactional
    public void invite(ConsolePrincipal principal, String email) {
        requireOwner(principal);
        accountService.createInvite(principal.organizationId(), principal.userId(), email);
    }

    private static void requireOwner(ConsolePrincipal principal) {
        if (!principal.owner()) {
            throw ApiException.forbidden("forbidden", "Owner role required");
        }
    }
}
