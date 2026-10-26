package com.kyc.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kyc.config.KycProperties;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.services.aml.Aml2;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kyc.opensanctions", name = "enabled", havingValue = "true")
public class OpenSanctionsMatchClient implements SanctionsMatchPort {

    private static final Logger log = LoggerFactory.getLogger(OpenSanctionsMatchClient.class);
    private static final Pattern FEATURE_KEY = Pattern.compile("[a-z0-9_]+");
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final URI base;
    private final HttpClient http;

    @Autowired
    public OpenSanctionsMatchClient(KycProperties properties, ObjectMapper objectMapper) {
        this(
                properties.opensanctions().apiKey(),
                objectMapper,
                URI.create("https://api.opensanctions.org"),
                HttpClient.newBuilder()
                        .connectTimeout(CALL_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    public OpenSanctionsMatchClient(String apiKey, ObjectMapper objectMapper, URI base, HttpClient http) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.objectMapper = objectMapper;
        this.base = base;
        this.http = http;
    }

    @Override
    public MatchResult match(Query query) {
        if (apiKey.isBlank()) {
            log.error("opensanctions.auth_rejected status=0");
            return new MatchResult.Failed("provider_unavailable");
        }
        String body = body(query);
        URI uri = base.resolve("/match/default?limit=20&threshold=" + query.threshold().toPlainString() + "&algorithm=" + Aml2.ALGORITHM);
        for (int attempt = 1; attempt <= 2; attempt++) {
            long started = System.nanoTime();
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(CALL_TIMEOUT)
                        .header("Authorization", "ApiKey " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                log.info(
                        "opensanctions.match status={} duration_ms={} attempt={}",
                        status,
                        (System.nanoTime() - started) / 1_000_000,
                        attempt);
                if (status == 200) {
                    return parse(response.body());
                }
                if (status == 401 || status == 403) {
                    log.error("opensanctions.auth_rejected status={}", status);
                    return new MatchResult.Failed("provider_unavailable", response.body());
                }
                if (retryable(status) && attempt == 1) {
                    continue;
                }
                if (retryable(status)) {
                    return new MatchResult.Failed("provider_unavailable", response.body());
                }
                return new MatchResult.Failed("provider_invalid_response", response.body());
            } catch (HttpTimeoutException ex) {
                log.info(
                        "opensanctions.match status=timeout duration_ms={} attempt={}",
                        (System.nanoTime() - started) / 1_000_000,
                        attempt);
                if (attempt == 1) {
                    continue;
                }
                return new MatchResult.Failed("provider_timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return new MatchResult.Failed("provider_unavailable");
            } catch (Exception ex) {
                log.info(
                        "opensanctions.match status=unavailable duration_ms={} attempt={}",
                        (System.nanoTime() - started) / 1_000_000,
                        attempt);
                if (attempt == 1) {
                    continue;
                }
                return new MatchResult.Failed("provider_unavailable");
            }
        }
        return new MatchResult.Failed("provider_unavailable");
    }

    private String body(Query query) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode queries = root.putObject("queries");
        ObjectNode person = queries.putObject(Aml2.QUERY_KEY);
        person.put("schema", "Person");
        ObjectNode properties = person.putObject("properties");
        put(properties, "name", query.name());
        if (query.birth() != null) {
            put(properties, "birthDate", query.birth().toString());
        }
        put(properties, "nationality", lower(query.nationality()));
        put(properties, "country", lower(query.countryOfResidence()));
        put(properties, "idNumber", query.idNumber());
        return root.toString();
    }

    private MatchResult parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode query = root.path("responses").path(Aml2.QUERY_KEY);
            JsonNode results = query.get("results");
            if (query.isMissingNode() || results == null || !results.isArray()) {
                return new MatchResult.Failed("provider_invalid_response", body);
            }
            if (query.has("status") && query.get("status").isNumber() && query.get("status").asInt() != 200) {
                return new MatchResult.Failed("provider_invalid_response", body);
            }
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode result : results) {
                candidates.add(candidate(result));
            }
            return new MatchResult.Ok(List.copyOf(candidates), body);
        } catch (Exception ex) {
            return new MatchResult.Failed("provider_invalid_response", body);
        }
    }

    private Candidate candidate(JsonNode result) {
        if (result == null || !result.isObject()) {
            return blank();
        }
        JsonNode properties = result.path("properties");
        List<String> aliases = strings(properties.path("alias"), 20);
        List<String> names = new ArrayList<>();
        String caption = text(result.get("caption"));
        if (caption != null) {
            names.add(caption);
        }
        for (String alias : aliases) {
            if (!alias.equals(caption)) {
                names.add(alias);
            }
        }
        BigDecimal score = decimal(result.get("score"));
        String id = text(result.get("id"));
        if (id != null && id.length() > 128) {
            id = null;
        }
        return new Candidate(
                id,
                score,
                text(result.get("schema")),
                result.path("target").asBoolean(false),
                strings(properties.path("topics"), 32),
                List.copyOf(names),
                strings(properties.path("birthDate"), 20),
                countries(properties),
                strings(properties.path("idNumber"), 20),
                strings(result.path("datasets"), 40),
                explanations(result.path("explanations")));
    }

    private static Candidate blank() {
        return new Candidate(null, null, null, false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private static List<String> countries(JsonNode properties) {
        List<String> values = new ArrayList<>();
        values.addAll(strings(properties.path("nationality"), 20));
        values.addAll(strings(properties.path("citizenship"), 20));
        values.addAll(strings(properties.path("country"), 20));
        return List.copyOf(values);
    }

    private static Map<String, BigDecimal> explanations(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, BigDecimal> features = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!FEATURE_KEY.matcher(entry.getKey()).matches() || !entry.getValue().isObject()) {
                return;
            }
            BigDecimal score = decimal(entry.getValue().get("score"));
            if (score != null) {
                features.put(entry.getKey(), score);
            }
        });
        return Map.copyOf(features);
    }

    private static List<String> strings(JsonNode node, int limit) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = text(item);
            if (text != null) {
                values.add(text);
            }
            if (values.size() == limit) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String lower(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void put(ObjectNode properties, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        ArrayNode array = properties.putArray(name);
        array.add(value);
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }
}
