package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.ports.StripePort;
import com.kyc.web.ApiException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kyc.stripe", name = "mode", havingValue = "stripe")
public class StripeCheckoutAdapter implements StripePort {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutAdapter.class);

    private final KycProperties properties;

    public StripeCheckoutAdapter(KycProperties properties) {
        this.properties = properties;
    }

    @Override
    public String ensureCustomer(UUID organizationId, String organizationName) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(organizationName)
                    .putMetadata("organization_id", organizationId.toString())
                    .build();
            return Customer.create(params, options()).getId();
        } catch (StripeException ex) {
            log.warn("stripe customer create failed code={}", ex.getCode());
            throw ApiException.dependencyUnavailable("Stripe is unavailable");
        }
    }

    @Override
    public CheckoutSession createCheckout(
            String customerId,
            long packMinor,
            UUID organizationId,
            UUID userId,
            String successUrl,
            String cancelUrl) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomer(customerId)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(properties.billing().currency())
                                    .setUnitAmount(packMinor)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Recogniz-Me credit")
                                            .build())
                                    .build())
                            .build())
                    .putMetadata("organization_id", organizationId.toString())
                    .putMetadata("pack_minor", Long.toString(packMinor))
                    .putMetadata("user_id", userId.toString())
                    .build();
            Session session = Session.create(params, options());
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException ex) {
            log.warn("stripe checkout create failed code={}", ex.getCode());
            throw ApiException.dependencyUnavailable("Stripe is unavailable");
        }
    }

    private RequestOptions options() {
        String key = properties.stripe().secretKey();
        if (key == null || key.isBlank()) {
            throw ApiException.dependencyUnavailable("Stripe is unavailable");
        }
        if (key.startsWith("pk_")) {
            throw ApiException.dependencyUnavailable(
                    "Stripe secret-key must be sk_test_…, not the publishable pk_ key");
        }
        if (key.startsWith("sk_live_")) {
            throw ApiException.dependencyUnavailable("Stripe live keys are not allowed yet");
        }
        return RequestOptions.builder().setApiKey(key).build();
    }
}
