package com.kyc.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kyc.config.KycProperties;
import com.kyc.ports.DocumentAuthenticityPort;
import com.kyc.ports.DocumentAuthenticityPort.AuthenticityRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpDocumentAuthenticity implements DocumentAuthenticityPort {

    private final String baseUrl;
    private final String token;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public HttpDocumentAuthenticity(KycProperties properties, ObjectMapper objectMapper) {
        this(
                properties.antiSpoof().baseUrl(),
                properties.antiSpoof().token(),
                properties.antiSpoof().timeoutMs(),
                objectMapper);
    }

    public HttpDocumentAuthenticity(String baseUrl, String token, int timeoutMs, ObjectMapper objectMapper) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token == null ? "" : token;
        this.timeout = Duration.ofMillis(Math.max(1, timeoutMs));
        this.mapper = objectMapper;
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public JsonNode analyze(byte[] jpeg) {
        return analyze(jpeg, null);
    }

    @Override
    public JsonNode analyze(byte[] jpeg, AuthenticityRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("request_id", UUID.randomUUID().toString());
        UUID verificationId = request == null || request.verificationId() == null
                ? UUID.randomUUID()
                : request.verificationId();
        body.put("verification_id", verificationId.toString());
        String side = request == null || request.side() == null || request.side().isBlank()
                ? "FRONT"
                : request.side();
        body.put("side", side);
        body.put("media_type", "image/jpeg");
        body.put("image_base64", Base64.getEncoder().encodeToString(jpeg));
        if (request != null && request.documentCode() != null && !request.documentCode().isBlank()) {
            ObjectNode hints = body.putObject("hints");
            hints.put("document_code", request.documentCode());
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/analyze"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new AuthenticityUnavailable("status");
            }
            JsonNode tree = mapper.readTree(response.body());
            if (!tree.isObject() || !tree.path("engine_version").isTextual() || !tree.path("alerts").isArray()) {
                throw new AuthenticityUnavailable("contract");
            }
            return tree;
        } catch (AuthenticityUnavailable ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new AuthenticityUnavailable("timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AuthenticityUnavailable("interrupted");
        } catch (Exception ex) {
            throw new AuthenticityUnavailable("io");
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
