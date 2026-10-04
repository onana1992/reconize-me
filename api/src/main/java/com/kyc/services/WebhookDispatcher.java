package com.kyc.services;

import com.kyc.enums.WebhookDeliveryStatus;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Integration;
import com.kyc.entities.WebhookDelivery;
import com.kyc.entities.WebhookEndpoint;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.WebhookDeliveryRepository;
import com.kyc.repositories.WebhookEndpointRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final IntegrationRepository integrations;
    private final AuditEventRepository auditEvents;
    private final WebhookService webhookService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebhookDispatcher(
            WebhookDeliveryRepository deliveries,
            WebhookEndpointRepository endpoints,
            IntegrationRepository integrations,
            AuditEventRepository auditEvents,
            WebhookService webhookService) {
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.integrations = integrations;
        this.auditEvents = auditEvents;
        this.webhookService = webhookService;
    }

    @Scheduled(fixedDelayString = "${kyc.webhook.poll-ms:5000}")
    @Transactional
    public void dispatchDue() {
        Instant now = Instant.now();
        List<WebhookDelivery> due = deliveries.findDue(now, PageRequest.of(0, 20));
        for (WebhookDelivery delivery : due) {
            try {
                deliverOne(delivery, now);
            } catch (Exception e) {
                log.warn("webhook dispatch error event_id={}", delivery.getId());
            }
        }
    }

    void deliverOne(WebhookDelivery delivery, Instant now) {
        WebhookEndpoint endpoint = endpoints.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint == null || !endpoint.isActive()) {
            delivery.markAttempt(5, null, "endpoint_missing", now, now);
            return;
        }
        Integration integration = integrations.findById(delivery.getIntegrationId()).orElse(null);
        boolean live = integration != null && integration.isLive();
        URI uri = URI.create(endpoint.getUrl());
        if (WebhookSsrfGuard.isDeniedAfterResolve(uri.getHost(), live)) {
            int attempt = delivery.getAttempt() + 1;
            Instant next = WebhookService.nextAttemptAt(attempt + 1, now);
            delivery.markAttempt(attempt, null, "ssrf_denied", next, now);
            if (delivery.getStatus() == WebhookDeliveryStatus.FAILED) {
                auditFailed(delivery, now);
            }
            return;
        }

        int attempt = delivery.getAttempt() + 1;
        String secret = webhookService.decryptSecret(endpoint);
        String body = delivery.getPayloadJson();
        long t = now.getEpochSecond();
        String signature = WebhookSigner.sign(secret, t, body);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "RecognizMe-Webhook/1")
                    .header("X-RecognizMe-Event", "verification.completed")
                    .header("X-RecognizMe-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                delivery.markDelivered(status, now);
                auditEvents.save(new AuditEvent(
                        delivery.getOrganizationId(),
                        "system",
                        null,
                        "webhook.delivered",
                        "webhook_delivery",
                        delivery.getId(),
                        "{\"event_id\":\"" + delivery.getId() + "\"}",
                        now));
                return;
            }
            String error = status >= 500 ? "http_5xx" : "http_4xx";
            Instant next = WebhookService.nextAttemptAt(attempt + 1, now);
            delivery.markAttempt(attempt, status, error, next, now);
            if (delivery.getStatus() == WebhookDeliveryStatus.FAILED) {
                auditFailed(delivery, now);
            }
        } catch (Exception e) {
            String error = e instanceof java.net.http.HttpTimeoutException ? "timeout" : "network";
            Instant next = WebhookService.nextAttemptAt(attempt + 1, now);
            delivery.markAttempt(attempt, null, error, next, now);
            if (delivery.getStatus() == WebhookDeliveryStatus.FAILED) {
                auditFailed(delivery, now);
            }
        }
    }

    private void auditFailed(WebhookDelivery delivery, Instant now) {
        auditEvents.save(new AuditEvent(
                delivery.getOrganizationId(),
                "system",
                null,
                "webhook.failed",
                "webhook_delivery",
                delivery.getId(),
                "{\"event_id\":\"" + delivery.getId() + "\"}",
                now));
    }
}
