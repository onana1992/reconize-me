package com.kyc.adapters;

import com.kyc.ports.MailPort;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingMailAdapter implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailAdapter.class);

    private final Map<String, String> lastUrls = new ConcurrentHashMap<>();
    private final Map<String, String> lastByTemplate = new ConcurrentHashMap<>();

    @Override
    public void send(String correlationId, String template, String url) {
        lastUrls.put(correlationId, url);
        lastByTemplate.put(template, url);
        log.info("mail correlation_id={} template={} url={}", correlationId, template, url);
    }

    @Override
    public Optional<String> lastUrl(String correlationId) {
        return Optional.ofNullable(lastUrls.get(correlationId));
    }

    @Override
    public Optional<String> lastUrlForTemplate(String template) {
        return Optional.ofNullable(lastByTemplate.get(template));
    }
}
