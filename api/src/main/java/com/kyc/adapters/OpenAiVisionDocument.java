package com.kyc.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.ports.VisionDocumentPort;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Appel vision OpenAI. Le secret reste dans la config ; ni l'image ni le texte lu ne sont journalisés. */
public class OpenAiVisionDocument implements VisionDocumentPort {

    static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String SYSTEM_PROMPT = """
            Reply with one JSON object only. Copy this shape. \
            detection is required and is one of NO_DOCUMENT, DOCUMENT_PRESENT, PARTIALLY_VISIBLE, \
            CROPPED, MULTIPLE_DOCUMENTS, TOO_SMALL, UNREADABLE. \
            classification.code is the code value from the catalog. classification.version is only the version value, such as 2024. classification.side is FRONT or BACK. \
            fields is an array. Each item has field, value as a string, confidence as a number from 0 to 1, \
            and source VISUAL_TEXT, MRZ, or VISUAL_AND_MRZ. The field name must be copied from the catalog line for the chosen code and side. Do not rename a printed label. Do not compute check digits. \
            Example: {"detection":"DOCUMENT_PRESENT","classification":{"code":"QUEBEC_DRIVER_LICENSE","version":"2024","confidence":0.9},"fields":[{"field":"lastName","value":"TREMBLAY","confidence":0.9,"source":"VISUAL_TEXT"}]}""";

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;
    private final String endpoint;
    private final String apiKey;

    public OpenAiVisionDocument(String apiKey, String model, ObjectMapper mapper) {
        this(apiKey, model, DEFAULT_ENDPOINT, DEFAULT_TIMEOUT, mapper);
    }

    OpenAiVisionDocument(String apiKey, String model, String endpoint, Duration timeout, ObjectMapper mapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("kyc.document-ia.openai-api-key is required when provider=openai");
        }
        if (endpoint == null || endpoint.isBlank() || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("OpenAI endpoint and timeout are required");
        }
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.endpoint = endpoint;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String complete(byte[] image, String mediaType, String prompt) {
        if (image == null || image.length == 0 || prompt == null || prompt.isBlank()) {
            throw new ProviderUnavailableException("vision_unavailable");
        }
        String dataUrl = "data:" + imageType(mediaType) + ";base64," + Base64.getEncoder().encodeToString(image);
        try {
            String payload = mapper.writeValueAsString(request(prompt, dataUrl));
            String response = client.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, clientResponse) -> {
                        byte[] body = clientResponse.getBody().readAllBytes();
                        if (clientResponse.getStatusCode().isError()) {
                            throw new ProviderUnavailableException(
                                    "openai_http_" + clientResponse.getStatusCode().value());
                        }
                        return new String(body, StandardCharsets.UTF_8);
                    });
            return contentOf(response);
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (RestClientException | JsonProcessingException e) {
            throw new ProviderUnavailableException("vision_unavailable", e);
        }
    }

    private Map<String, Object> request(String prompt, String dataUrl) {
        return Map.of(
                "model", model,
                "temperature", 0,
                "max_completion_tokens", 4096,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));
    }

    private String contentOf(String response) {
        if (response == null || response.isBlank()) {
            throw new ProviderUnavailableException("vision_unavailable");
        }
        try {
            JsonNode content = mapper.readTree(response).path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new ProviderUnavailableException("vision_unavailable");
            }
            return content.asText();
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new ProviderUnavailableException("vision_unavailable", e);
        }
    }

    private static String imageType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return "image/jpeg";
        }
        String base = mediaType.split(";", 2)[0].trim().toLowerCase();
        return base.startsWith("image/") ? base : "image/jpeg";
    }
}
