package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.dto.documentia.DocumentIaAnalysisResponse;
import com.kyc.dto.documentia.DocumentIaCatalogResponse;
import com.kyc.dto.documentia.DocumentParse;
import com.kyc.security.ApiPrincipal;
import com.kyc.services.documentia.DocumentAnalysisParser;
import com.kyc.services.documentia.InvalidModelJsonException;
import com.kyc.services.documentia.SchemaRegistry;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIaLabService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIaLabService.class);

    static final Set<String> STAGES = Set.of("quality", "parse", "vision", "normalize", "mrz", "validate", "decide");

    private static final Pattern FIXTURE_NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final KycProperties properties;
    private final IntegrationService integrations;
    private final DocumentAnalysisParser parser;
    private final SchemaRegistry registry;

    public DocumentIaLabService(
            KycProperties properties,
            IntegrationService integrations,
            DocumentAnalysisParser parser,
            SchemaRegistry registry) {
        this.properties = properties;
        this.integrations = integrations;
        this.parser = parser;
        this.registry = registry;
    }

    public DocumentIaCatalogResponse catalog() {
        requireEnabled();
        return registry.catalog();
    }

    public DocumentIaAnalysisResponse fixture(String name) {
        requireEnabled();
        if (name == null || !FIXTURE_NAME.matcher(name).matches()) {
            throw ApiException.notFound("Fixture not found");
        }
        ClassPathResource resource = new ClassPathResource("document-ia/" + name + ".json");
        if (!resource.exists()) {
            throw ApiException.notFound("Fixture not found");
        }
        String json;
        try {
            json = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.notFound("Fixture not found");
        }
        long start = System.nanoTime();
        try {
            DocumentParse parsed = parser.parse(json);
            log.info(
                    "document-ia fixture parsed durationMs={} code={} providerCalled=false",
                    (System.nanoTime() - start) / 1_000_000L,
                    parsed.document().classification().code());
            return DocumentIaAnalysisResponse.parsed(parsed.document(), parsed.schemaVersionId());
        } catch (InvalidModelJsonException e) {
            log.info(
                    "document-ia fixture rejected durationMs={}",
                    (System.nanoTime() - start) / 1_000_000L);
            throw ApiException.unprocessable("invalid_model_json", "Model JSON does not match the document contract");
        }
    }

    public DocumentIaAnalysisResponse analyze(ApiPrincipal principal, String until, MultipartFile file) {
        requireEnabled();
        var integration = integrations.requireInOrg(principal.organizationId(), principal.integrationId());
        if (!integration.isLive()) {
            throw ApiException.forbidden(
                    "sandbox_no_vision", "Document IA lab does not call the vision provider for test keys");
        }
        parseUntil(until);
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("File is required", List.of(new ErrorDetail("file", "required")));
        }
        long start = System.nanoTime();
        DocumentIaAnalysisResponse response = DocumentIaAnalysisResponse.empty();
        log.info(
                "document-ia lab completed durationMs={} providerCalled={}",
                (System.nanoTime() - start) / 1_000_000L,
                response.providerCalled());
        return response;
    }

    private void requireEnabled() {
        if (!properties.documentIa().labEnabled()) {
            throw ApiException.notFound("Not found");
        }
    }

    static void parseUntil(String until) {
        if (until == null || until.isBlank()) {
            return;
        }
        if (!STAGES.contains(until.trim().toLowerCase(Locale.ROOT))) {
            throw ApiException.validation("Invalid until", List.of(new ErrorDetail("until", "invalid")));
        }
    }
}
