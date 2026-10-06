package com.kyc.ports;

import java.util.Map;
import java.util.Optional;

/**
 * Port for sending mail, typically for delivering links/codes, and retrieving
 * the last sent URLs for debugging or verification.
 */
public interface MailPort {

    /**
     * Sends an email to the specified recipient.
     *
     * @param to recipient email address
     * @param correlationId an identifier to correlate this communication (e.g. flow or user)
     * @param template the template identifier for the email content
     * @param url a URL to include in the email (e.g. for verification or action)
     */
    void send(String to, String correlationId, String template, String url);

    /**
     * Sends an email with additional metadata.
     *
     * @param to recipient email address
     * @param correlationId an identifier to correlate this communication
     * @param template the template identifier for the email content
     * @param url a URL to include in the email
     * @param extras a map of extra key-value data for the template or provider (e.g. username, details)
     */
    default void send(String to, String correlationId, String template, String url, Map<String, String> extras) {
        // Default implementation ignores extras.
        send(to, correlationId, template, url);
    }

    /**
     * Retrieves the last URL sent for a given correlation ID, if available.
     *
     * @param correlationId identifier used during sending
     * @return an Optional containing the last URL sent for the correlationId, if any
     */
    Optional<String> lastUrl(String correlationId);

    /**
     * Retrieves the last URL sent for a specific template, regardless of correlation ID, if available.
     *
     * @param template email template identifier
     * @return an Optional containing the last URL sent for the specified template, if any
     */
    Optional<String> lastUrlForTemplate(String template);
}
