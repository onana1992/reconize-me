package com.kyc.ports;

import java.util.Optional;
import java.util.UUID;

public interface MailPort {

    void send(String correlationId, String template, String url);

    Optional<String> lastUrl(String correlationId);

    Optional<String> lastUrlForTemplate(String template);
}
