package com.kyc.ports;

import java.util.Optional;

public interface MailPort {

    void send(String to, String correlationId, String template, String url);

    Optional<String> lastUrl(String correlationId);

    Optional<String> lastUrlForTemplate(String template);
}
