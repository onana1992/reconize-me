package com.kyc.adapters;

import com.kyc.ports.StripePort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kyc.stripe", name = "mode", havingValue = "log", matchIfMissing = true)
public class LoggingStripeAdapter implements StripePort {

    private static final Logger log = LoggerFactory.getLogger(LoggingStripeAdapter.class);

    @Override
    public String ensureCustomer(UUID organizationId, String organizationName) {
        return "cus_log_" + organizationId.toString().replace("-", "");
    }

    @Override
    public CheckoutSession createCheckout(
            String customerId,
            long packMinor,
            UUID organizationId,
            UUID userId,
            String successUrl,
            String cancelUrl) {
        String id = "cs_log_" + UUID.randomUUID().toString().replace("-", "");
        log.warn("kyc.stripe.mode=log: fake Checkout URL (not a Stripe session). Set kyc.stripe.mode=stripe");
        return new CheckoutSession(id, "https://checkout.stripe.com/c/pay/" + id);
    }
}
