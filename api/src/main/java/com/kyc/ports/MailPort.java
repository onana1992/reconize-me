package com.kyc.ports;

import java.util.Map;
import java.util.Optional;

public interface MailPort {

    void send(String to, String correlationId, String template, String url);

    default void send(String to, String correlationId, String template, String url, Map<String, String> extras) {
        send(to, correlationId, template, url);
    }

    Optional<String> lastUrl(String correlationId);

    Optional<String> lastUrlForTemplate(String template);
}
