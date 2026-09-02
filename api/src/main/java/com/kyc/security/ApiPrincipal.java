package com.kyc.security;

import java.util.UUID;

public record ApiPrincipal(UUID organizationId, UUID apiKeyId) {}
