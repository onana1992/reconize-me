package com.kyc.ports;

import java.util.UUID;

// Interface for Stripe operations related to customer and checkout session management.
public interface StripePort {

    /**
     * Ensures there is a Stripe customer for the given organization.
     * If it doesn't exist, creates it and returns the customer ID.
     *
     * @param organizationId the unique identifier of the organization
     * @param organizationName the name of the organization
     * @return the Stripe customer ID
     */
    String ensureCustomer(UUID organizationId, String organizationName);

    /**
     * Creates a Stripe checkout session for purchasing a pack.
     *
     * @param customerId the Stripe customer ID
     * @param packMinor the amount in minor currency units
     * @param organizationId the unique identifier of the organization
     * @param userId the unique identifier of the user
     * @param successUrl the URL to redirect to after successful payment
     * @param cancelUrl the URL to redirect to if the payment is cancelled
     * @return a representation of the created checkout session
     */
    CheckoutSession createCheckout(
            String customerId,
            long packMinor,
            UUID organizationId,
            UUID userId,
            String successUrl,
            String cancelUrl);

    /**
     * Record representing a Stripe Checkout Session.
     *
     * @param id the session ID
     * @param url the URL for the checkout session
     */
    record CheckoutSession(String id, String url) {}
}
