package com.kyc.services;

import com.kyc.dto.account.IssuedApiKeyResponse;
import com.kyc.dto.console.ApiKeyListItem;
import com.kyc.entities.ApiKey;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Integration;
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
    private final SecretCipher secretCipher;

    public ApiKeyIssuer(
            ApiKeyRepository apiKeyRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            SecretCipher secretCipher) {
        this.apiKeyRepository = apiKeyRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.secretCipher = secretCipher;
    }

    public IssuedApiKeyResponse issue(Integration integration, UUID createdByUserId) {
        Instant now = Instant.now();
        String raw = CryptoTokens.randomApiKey(integration.isLive());
        String prefix = raw.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH);
        UUID id = UUID.randomUUID();
        ApiKey saved = new ApiKey(
                id,
                integration.getOrganizationId(),
                integration.getId(),
                prefix,
                passwordEncoder.encode(raw),
                now,
                createdByUserId);
        saved.storeCipher(secretCipher.encrypt(raw));
        apiKeyRepository.save(saved);
        auditEventRepository.save(new AuditEvent(
                integration.getOrganizationId(),
                "user",
                createdByUserId,
                "api_key.issued",
                "api_key",
                id,
                "{}",
                now));
        return new IssuedApiKeyResponse(id, raw, prefix, integration.getId());
    }

    public ApiKeyListItem toListItem(ApiKey key) {
        String secret = key.getKeyCipher() == null ? null : secretCipher.decrypt(key.getKeyCipher());
        return new ApiKeyListItem(
                key.getId(), key.getIntegrationId(), key.getKeyPrefix(), key.getCreatedAt(), key.isRevoked(), secret);
    }
}
