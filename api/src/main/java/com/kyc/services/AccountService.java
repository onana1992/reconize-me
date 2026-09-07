package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.account.SignupResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.EmailVerificationToken;
import com.kyc.entities.Membership;
import com.kyc.entities.MembershipInvite;
import com.kyc.entities.Organization;
import com.kyc.entities.PasswordResetToken;
import com.kyc.entities.User;
import com.kyc.ports.MailPort;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.EmailVerificationTokenRepository;
import com.kyc.repositories.MembershipInviteRepository;
import com.kyc.repositories.MembershipRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.PasswordResetTokenRepository;
import com.kyc.repositories.UserRepository;
import com.kyc.security.ConsolePrincipal;
import com.kyc.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);
    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MembershipInviteRepository membershipInviteRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailPort mailPort;
    private final KycProperties properties;
    private final ApiKeyIssuer apiKeyIssuer;
    private final SessionService sessionService;

    public AccountService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            MembershipInviteRepository membershipInviteRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            MailPort mailPort,
            KycProperties properties,
            ApiKeyIssuer apiKeyIssuer,
            SessionService sessionService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.membershipInviteRepository = membershipInviteRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailPort = mailPort;
        this.properties = properties;
        this.apiKeyIssuer = apiKeyIssuer;
        this.sessionService = sessionService;
    }

    @Transactional
    public SignupResponse signup(
            String email,
            String password,
            String organizationName,
            String inviteToken,
            String firstName,
            String lastName) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("email_taken", "An account with this email already exists");
        }

        Instant now = Instant.now();
        MembershipInvite invite = null;
        if (inviteToken != null && !inviteToken.isBlank()) {
            invite = resolveInvite(inviteToken, now).orElseThrow(ApiException::invalidOrExpiredToken);
            if (!invite.getEmail().equals(normalizedEmail)) {
                throw ApiException.invalidOrExpiredToken();
            }
        }

        UUID userId = UUID.randomUUID();
        User user = new User(userId, normalizedEmail, passwordEncoder.encode(password), now);
        user.setFirstName(blankToNull(firstName));
        user.setLastName(blankToNull(lastName));
        userRepository.save(user);

        if (invite == null) {
            UUID organizationId = UUID.randomUUID();
            String slug = OrganizationSlugs.unique(
                    OrganizationSlugs.fromName(organizationName), organizationRepository::existsBySlug);
            organizationRepository.save(new Organization(organizationId, organizationName.trim(), slug, now));
            membershipRepository.save(new Membership(userId, organizationId, Membership.ROLE_OWNER, now));
            auditEventRepository.save(new AuditEvent(
                    organizationId, "user", userId, "user.registered", "user", userId, "{}", now));
        } else {
            auditEventRepository.save(new AuditEvent(
                    invite.getOrganizationId(), "user", userId, "user.registered", "user", userId, "{}", now));
        }

        issueVerificationMail(user, now);
        return new SignupResponse(userId);
    }

    @Transactional
    public IssuedApiKeyResponse verify(String rawToken) {
        Instant now = Instant.now();
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(hashToken(rawToken))
                .filter(row -> row.usable(now))
                .orElseThrow(ApiException::invalidOrExpiredToken);

        User user = userRepository.findById(token.getUserId()).orElseThrow(ApiException::invalidOrExpiredToken);
        token.consume(now);
        if (!user.isVerified()) {
            user.markVerified(now);
        }

        Membership membership = membershipRepository.findByUserId(user.getId()).orElse(null);
        if (membership == null) {
            MembershipInvite invite = membershipInviteRepository
                    .findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(user.getEmail())
                    .filter(row -> row.pending(now))
                    .orElseThrow(ApiException::invalidOrExpiredToken);
            membership = membershipRepository.save(new Membership(
                    user.getId(), invite.getOrganizationId(), Membership.ROLE_MEMBER, now));
            invite.accept(now);
        }

        auditEventRepository.save(new AuditEvent(
                membership.getOrganizationId(),
                "user",
                user.getId(),
                "user.email_verified",
                "user",
                user.getId(),
                "{}",
                now));

        if (membership.isOwner()) {
            return apiKeyIssuer.issue(membership.getOrganizationId(), user.getId());
        }
        return new IssuedApiKeyResponse(null, null, null);
    }

    @Transactional
    public void resend(String email) {
        String normalized = normalizeEmail(email);
        Optional<User> user = userRepository.findByEmail(normalized);
        if (user.isEmpty() || user.get().isVerified()) {
            return;
        }
        issueVerificationMail(user.get(), Instant.now());
    }

    @Transactional
    public String login(String email, String password) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.info("user.login_failed");
            throw ApiException.unauthorized("invalid_credentials", INVALID_CREDENTIALS);
        }
        if (!user.isVerified()) {
            throw ApiException.forbidden("email_unverified", "Verify your email before signing in");
        }
        Membership membership = membershipRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> ApiException.unauthorized("invalid_credentials", INVALID_CREDENTIALS));
        return sessionService.create(new ConsolePrincipal(user.getId(), membership.getOrganizationId(), membership.getRole()));
    }

    public void logout(String rawSession) {
        sessionService.invalidate(rawSession);
    }

    @Transactional
    public void forgot(String email) {
        String normalized = normalizeEmail(email);
        Optional<User> found = userRepository.findByEmail(normalized);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        Instant now = Instant.now();
        for (PasswordResetToken existing : passwordResetTokenRepository.findByUserIdAndConsumedAtIsNull(user.getId())) {
            existing.consume(now);
        }
        String raw = CryptoTokens.randomHostedToken();
        passwordResetTokenRepository.save(
                new PasswordResetToken(UUID.randomUUID(), user.getId(), hashToken(raw), now.plus(RESET_TTL)));
        mailPort.send(user.getEmail(), user.getId().toString(), "password_reset", properties.consoleUrl("/reset?token=" + raw));
    }

    @Transactional
    public void reset(String rawToken, String password) {
        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(hashToken(rawToken))
                .filter(row -> row.usable(now))
                .orElseThrow(ApiException::invalidOrExpiredToken);
        User user = userRepository.findById(token.getUserId()).orElseThrow(ApiException::invalidOrExpiredToken);
        token.consume(now);
        user.setPasswordHash(passwordEncoder.encode(password));
    }

    @Transactional
    public void acceptInvite(String rawToken) {
        Instant now = Instant.now();
        MembershipInvite invite = membershipInviteRepository
                .findByTokenHash(hashToken(rawToken))
                .filter(row -> row.pending(now))
                .orElseThrow(ApiException::invalidOrExpiredToken);
        User user = userRepository
                .findByEmail(invite.getEmail())
                .orElseThrow(ApiException::invalidOrExpiredToken);
        if (membershipRepository.existsByUserId(user.getId())) {
            throw ApiException.conflict("already_in_organization", "This account already belongs to an organization");
        }
        membershipRepository.save(new Membership(user.getId(), invite.getOrganizationId(), Membership.ROLE_MEMBER, now));
        invite.accept(now);
    }

    @Transactional
    public MembershipInvite createInvite(UUID organizationId, UUID actorUserId, String email) {
        String normalized = normalizeEmail(email);
        Instant now = Instant.now();
        Optional<User> existing = userRepository.findByEmail(normalized);
        if (existing.isPresent() && membershipRepository.existsByOrganizationIdAndUserId(organizationId, existing.get().getId())) {
            throw ApiException.conflict("already_member", "This email is already a member");
        }
        String raw = CryptoTokens.randomHostedToken();
        MembershipInvite invite = membershipInviteRepository.save(new MembershipInvite(
                UUID.randomUUID(),
                organizationId,
                normalized,
                Membership.ROLE_MEMBER,
                hashToken(raw),
                now.plus(INVITE_TTL),
                now));
        String correlation = existing.map(user -> user.getId().toString()).orElse("invite:" + invite.getId());
        mailPort.send(normalized, correlation, "team_invite", properties.consoleUrl("/signup?invite=" + raw));
        auditEventRepository.save(new AuditEvent(
                organizationId, "user", actorUserId, "membership.invited", "membership_invite", invite.getId(), "{}", now));
        return invite;
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("invalid_credentials", INVALID_CREDENTIALS);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    private void issueVerificationMail(User user, Instant now) {
        for (EmailVerificationToken existing :
                emailVerificationTokenRepository.findByUserIdAndConsumedAtIsNull(user.getId())) {
            existing.consume(now);
        }
        String raw = CryptoTokens.randomHostedToken();
        emailVerificationTokenRepository.save(
                new EmailVerificationToken(UUID.randomUUID(), user.getId(), hashToken(raw), now.plus(VERIFY_TTL)));
        mailPort.send(user.getEmail(), user.getId().toString(), "email_verify", properties.consoleUrl("/verify?token=" + raw));
    }

    private Optional<MembershipInvite> resolveInvite(String inviteToken, Instant now) {
        if (inviteToken == null || inviteToken.isBlank()) {
            return Optional.empty();
        }
        return membershipInviteRepository.findByTokenHash(hashToken(inviteToken)).filter(row -> row.pending(now));
    }

    private String hashToken(String raw) {
        return CryptoTokens.sha256HexPeppered(properties.ipHashPepper(), raw);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
