package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.dto.billing.BillingResponse;
import com.kyc.dto.billing.CheckoutResponse;
import com.kyc.dto.billing.UsageResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.CreditAccount;
import com.kyc.entities.CreditLedgerEntry;
import com.kyc.entities.Integration;
import com.kyc.entities.StripeCustomer;
import com.kyc.ports.StripePort;
import com.kyc.repositories.CreditAccountRepository;
import com.kyc.repositories.CreditLedgerEntryRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.StripeCustomerRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.Permission;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditService {

    public static final String PRODUCT_IDENTITY = Integration.PRODUCT_IDENTITY;

    private static final Logger log = LoggerFactory.getLogger(CreditService.class);
    private static final int LEDGER_DEFAULT = 50;
    private static final int LEDGER_MAX = 100;

    private final CreditAccountRepository accounts;
    private final CreditLedgerEntryRepository ledger;
    private final StripeCustomerRepository stripeCustomers;
    private final VerificationRepository verifications;
    private final OrganizationRepository organizations;
    private final AuditEventRepositoryAdapter audit;
    private final StripePort stripe;
    private final KycProperties properties;

    public CreditService(
            CreditAccountRepository accounts,
            CreditLedgerEntryRepository ledger,
            StripeCustomerRepository stripeCustomers,
            VerificationRepository verifications,
            OrganizationRepository organizations,
            com.kyc.repositories.AuditEventRepository auditEvents,
            StripePort stripe,
            KycProperties properties) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.stripeCustomers = stripeCustomers;
        this.verifications = verifications;
        this.organizations = organizations;
        this.audit = new AuditEventRepositoryAdapter(auditEvents);
        this.stripe = stripe;
        this.properties = properties;
    }

    @Transactional
    public CreditAccount ensureAccount(UUID organizationId) {
        return accounts.findById(organizationId).orElseGet(() -> createAccount(organizationId));
    }

    @Transactional(readOnly = true)
    public boolean coversUnit(UUID organizationId) {
        long balance = accounts.findById(organizationId).map(CreditAccount::getBalanceMinor).orElse(0L);
        return balance >= properties.billing().unitAmountMinor();
    }

    @Transactional(readOnly = true)
    public BillingResponse billing(ConsolePrincipal principal, String cursor, Integer limit) {
        ConsoleAuth.require(principal, Permission.BILLING_READ);
        return snapshot(principal.organizationId(), cursor, limit, true);
    }

    @Transactional(readOnly = true)
    public BillingResponse summary(UUID organizationId) {
        return snapshot(organizationId, null, 1, false);
    }

    @Transactional(readOnly = true)
    public UsageResponse usage(UUID organizationId) {
        BillingResponse full = snapshot(organizationId, null, 1, false);
        return new UsageResponse(full.currency(), full.balanceMinor(), full.usage());
    }

    @Transactional(readOnly = true)
    public BillingResponse.ProductUsage identityUsage(UUID organizationId) {
        return snapshot(organizationId, null, 1, false).usage().get(0);
    }

    @Transactional(readOnly = true)
    public long balanceMinor(UUID organizationId) {
        return accounts.findById(organizationId).map(CreditAccount::getBalanceMinor).orElse(0L);
    }

    @Transactional
    public CheckoutResponse createCheckout(ConsolePrincipal principal, Long packMinor) {
        ConsoleAuth.require(principal, Permission.BILLING_WRITE);
        if (packMinor == null || !properties.billing().isAllowedPack(packMinor)) {
            throw ApiException.validation("Invalid pack", List.of(new ErrorDetail("pack_minor", "invalid")));
        }
        ensureAccount(principal.organizationId());
        String orgName = organizations
                .findById(principal.organizationId())
                .map(org -> org.getName())
                .orElse("Recogniz-Me");
        String customerId = resolveStripeCustomer(principal.organizationId(), orgName);
        String success = properties.consoleUrl("/settings/billing?checkout=success");
        String cancel = properties.consoleUrl("/settings/billing?checkout=cancel");
        StripePort.CheckoutSession session = stripe.createCheckout(
                customerId, packMinor, principal.organizationId(), principal.userId(), success, cancel);
        audit.save(
                principal.organizationId(),
                "user",
                principal.userId(),
                "billing.checkout_created",
                "organization",
                principal.organizationId(),
                "{\"pack_minor\":" + packMinor + "}",
                Instant.now());
        return new CheckoutResponse(session.url());
    }

    @Transactional
    public void topupFromCheckout(
            String stripeEventId,
            String checkoutSessionId,
            String stripeCustomerId,
            UUID metadataOrganizationId,
            UUID metadataUserId,
            long packMinor) {
        if (stripeEventId != null && ledger.existsByStripeEventId(stripeEventId)) {
            return;
        }
        if (checkoutSessionId != null && ledger.existsByStripeCheckoutSessionId(checkoutSessionId)) {
            return;
        }
        StripeCustomer customer = stripeCustomers.findByStripeCustomerId(stripeCustomerId).orElse(null);
        if (customer == null
                || metadataOrganizationId == null
                || !customer.getOrganizationId().equals(metadataOrganizationId)
                || !properties.billing().isAllowedPack(packMinor)) {
            log.info("stripe webhook event_id={} code=topup_ignored", stripeEventId);
            if (customer != null) {
                audit.save(
                        customer.getOrganizationId(),
                        "system",
                        null,
                        "credit.topup_ignored",
                        "organization",
                        customer.getOrganizationId(),
                        "{\"event_id\":\"" + safe(stripeEventId) + "\"}",
                        Instant.now());
            }
            return;
        }
        CreditAccount account = lockOrCreate(customer.getOrganizationId());
        if (stripeEventId != null && ledger.existsByStripeEventId(stripeEventId)) {
            return;
        }
        if (checkoutSessionId != null && ledger.existsByStripeCheckoutSessionId(checkoutSessionId)) {
            return;
        }
        Instant now = Instant.now();
        account.apply(packMinor, now);
        UUID entryId = UUID.randomUUID();
        try {
            ledger.save(new CreditLedgerEntry(
                    entryId,
                    account.getOrganizationId(),
                    CreditLedgerEntry.TOPUP,
                    packMinor,
                    account.getBalanceMinor(),
                    null,
                    null,
                    null,
                    stripeEventId,
                    checkoutSessionId,
                    metadataUserId,
                    now));
        } catch (DataIntegrityViolationException ignored) {
            return;
        }
        audit.save(
                account.getOrganizationId(),
                "system",
                metadataUserId,
                "credit.topped_up",
                "credit_ledger_entry",
                entryId,
                "{\"amount_minor\":" + packMinor + "}",
                now);
    }

    @Transactional
    public void debitForLiveVerification(UUID organizationId, UUID verificationId, UUID actorId, String actorType) {
        CreditAccount account = lockOrCreate(organizationId);
        long unit = properties.billing().unitAmountMinor();
        if (account.getBalanceMinor() < unit) {
            throw ApiException.insufficientCredit();
        }
        if (ledger.existsByResourceTypeAndResourceId(CreditLedgerEntry.RESOURCE_VERIFICATION, verificationId)) {
            return;
        }
        Instant now = Instant.now();
        account.apply(-unit, now);
        UUID entryId = UUID.randomUUID();
        ledger.save(new CreditLedgerEntry(
                entryId,
                organizationId,
                CreditLedgerEntry.DEBIT,
                -unit,
                account.getBalanceMinor(),
                PRODUCT_IDENTITY,
                CreditLedgerEntry.RESOURCE_VERIFICATION,
                verificationId,
                null,
                null,
                actorId,
                now));
        audit.save(
                organizationId,
                actorType == null ? "system" : actorType,
                actorId,
                "credit.debited",
                "verification",
                verificationId,
                "{\"amount_minor\":" + unit + ",\"product\":\"identity\"}",
                now);
    }

    private BillingResponse snapshot(UUID organizationId, String cursor, Integer limit, boolean includeLedger) {
        CreditAccount account = accounts.findById(organizationId).orElse(null);
        long unit = properties.billing().unitAmountMinor();
        long balance = account == null ? 0 : account.getBalanceMinor();
        String currency = account == null ? properties.billing().currency() : account.getCurrency();
        long sandbox = 0;
        long live = 0;
        for (Object[] row : verifications.countByIntegrationMode(organizationId)) {
            String mode = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            if (Integration.MODE_LIVE.equals(mode)) {
                live = count;
            } else {
                sandbox += count;
            }
        }
        long debit = ledger.sumDebits(organizationId, PRODUCT_IDENTITY);
        List<BillingResponse.ProductUsage> usage = List.of(
                new BillingResponse.ProductUsage(PRODUCT_IDENTITY, sandbox, live, debit));
        BillingResponse.LedgerPage page = includeLedger
                ? ledgerPage(organizationId, cursor, limit)
                : new BillingResponse.LedgerPage(List.of(), null);
        return new BillingResponse(
                currency,
                balance,
                unit,
                balance >= unit,
                properties.billing().packs(),
                usage,
                page);
    }

    private BillingResponse.LedgerPage ledgerPage(UUID organizationId, String cursor, Integer limit) {
        int size = limit == null ? LEDGER_DEFAULT : Math.min(Math.max(limit, 1), LEDGER_MAX);
        List<CreditLedgerEntry> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = ledger.findByOrganizationIdOrderByCreatedAtDescIdDesc(
                    organizationId, PageRequest.of(0, size + 1));
        } else {
            Cursor decoded = Cursor.parse(cursor);
            rows = ledger.pageAfter(organizationId, decoded.createdAt(), decoded.id(), PageRequest.of(0, size + 1));
        }
        String next = null;
        if (rows.size() > size) {
            rows = new ArrayList<>(rows.subList(0, size));
            CreditLedgerEntry last = rows.get(rows.size() - 1);
            next = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        List<BillingResponse.LedgerEntryResponse> entries = rows.stream()
                .map(row -> new BillingResponse.LedgerEntryResponse(
                        row.getId(),
                        row.getEntryType(),
                        row.getAmountMinor(),
                        row.getBalanceAfterMinor(),
                        row.getProduct(),
                        row.getResourceType(),
                        row.getResourceId(),
                        row.getCreatedAt()))
                .toList();
        return new BillingResponse.LedgerPage(entries, next);
    }

    private String resolveStripeCustomer(UUID organizationId, String organizationName) {
        StripeCustomer existing = stripeCustomers.findById(organizationId).orElse(null);
        if (existing != null && isStripeCustomer(existing.getStripeCustomerId())) {
            return existing.getStripeCustomerId();
        }
        String customerId = stripe.ensureCustomer(organizationId, organizationName);
        if (existing == null) {
            try {
                stripeCustomers.save(new StripeCustomer(organizationId, customerId, Instant.now()));
            } catch (DataIntegrityViolationException ex) {
                StripeCustomer raced = stripeCustomers.findById(organizationId).orElse(null);
                if (raced != null && isStripeCustomer(raced.getStripeCustomerId())) {
                    return raced.getStripeCustomerId();
                }
                if (raced != null) {
                    raced.replaceCustomerId(customerId);
                    return customerId;
                }
            }
        } else {
            existing.replaceCustomerId(customerId);
        }
        return customerId;
    }

    private static boolean isStripeCustomer(String customerId) {
        return customerId != null && customerId.startsWith("cus_") && !customerId.startsWith("cus_log_");
    }

    private CreditAccount lockOrCreate(UUID organizationId) {
        return accounts.lockById(organizationId).orElseGet(() -> {
            createAccount(organizationId);
            return accounts.lockById(organizationId).orElseThrow();
        });
    }

    private CreditAccount createAccount(UUID organizationId) {
        Instant now = Instant.now();
        try {
            return accounts.save(new CreditAccount(organizationId, properties.billing().currency(), 0, now));
        } catch (DataIntegrityViolationException ex) {
            return accounts.findById(organizationId).orElseThrow();
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "");
    }

    private record Cursor(Instant createdAt, UUID id) {
        static Cursor parse(String raw) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(raw));
                int sep = decoded.indexOf('|');
                return new Cursor(Instant.parse(decoded.substring(0, sep)), UUID.fromString(decoded.substring(sep + 1)));
            } catch (RuntimeException ex) {
                throw ApiException.validation("Invalid cursor", List.of(new ErrorDetail("cursor", "invalid")));
            }
        }

        String encode() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString((createdAt + "|" + id).getBytes());
        }
    }

    private record AuditEventRepositoryAdapter(com.kyc.repositories.AuditEventRepository events) {
        void save(
                UUID organizationId,
                String actorType,
                UUID actorId,
                String action,
                String resourceType,
                UUID resourceId,
                String payload,
                Instant now) {
            events.save(new AuditEvent(organizationId, actorType, actorId, action, resourceType, resourceId, payload, now));
        }
    }
}
