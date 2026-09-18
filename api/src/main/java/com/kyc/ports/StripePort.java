package com.kyc.ports;

import java.util.UUID;

public interface StripePort {

    String ensureCustomer(UUID organizationId, String organizationName);

    CheckoutSession createCheckout(
            String customerId,
            long packMinor,
            UUID organizationId,
            UUID userId,
            String successUrl,
            String cancelUrl);

    record CheckoutSession(String id, String url) {}
}
