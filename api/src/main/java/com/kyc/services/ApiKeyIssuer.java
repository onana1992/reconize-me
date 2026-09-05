package com.kyc.services;

import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.entities.ApiKey;
import com.kyc.entities.AuditEvent;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.AuditEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyIssuer {

    private final ApiKeyRepository apiKeyRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyIssuer(
            ApiKeyRepository apiKeyRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public IssuedApiKeyResponse issue(UUID organizationId, UUID createdByUserId) {
        Instant now = Instant.now();
        String raw = CryptoTokens.randomApiKey();
        String prefix = raw.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH);
        UUID id = UUID.randomUUID();
        apiKeyRepository.save(new ApiKey(id, organizationId, prefix, passwordEncoder.encode(raw), now, createdByUserId));
        auditEventRepository.save(new AuditEvent(
                organizationId, "user", createdByUserId, "api_key.issued", "api_key", id, "{}", now));
        return new IssuedApiKeyResponse(id, raw, prefix);
    }
}
