package com.kyc.services;

import com.kyc.entities.ApiKey;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.security.ApiPrincipal;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyAuthenticator {

    public static final int PREFIX_LENGTH = 12;

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyAuthenticator(ApiKeyRepository apiKeyRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<ApiPrincipal> authenticate(String rawKey) {
        if (rawKey == null || rawKey.length() < PREFIX_LENGTH) {
            return Optional.empty();
        }
        if (!rawKey.startsWith("ky_test_") && !rawKey.startsWith("ky_live_")) {
            return Optional.empty();
        }
        String prefix = rawKey.substring(0, PREFIX_LENGTH);
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndRevokedFalse(prefix);
        for (ApiKey key : candidates) {
            if (passwordEncoder.matches(rawKey, key.getKeyHash())) {
                return Optional.of(new ApiPrincipal(key.getOrganizationId(), key.getId(), key.getIntegrationId()));
            }
        }
        return Optional.empty();
    }
}
