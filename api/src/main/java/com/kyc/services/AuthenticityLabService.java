package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.dto.authenticity.AuthenticityCheckResponse;
import com.kyc.dto.authenticity.AuthenticityTemplatesResponse;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthenticityLabService {

    static final Set<String> STAGES = Set.of("rectify", "project", "fixed", "positions", "decide");

    private final KycProperties properties;

    public AuthenticityLabService(KycProperties properties) {
        this.properties = properties;
    }

    public AuthenticityTemplatesResponse templates() {
        requireEnabled();
        // A0 : registre vide. A1 chargera classpath:authenticity/{documentId}/.
        return new AuthenticityTemplatesResponse(List.of());
    }

    public AuthenticityCheckResponse check(String documentId, String until, MultipartFile file) {
        requireEnabled();
        String stoppedAt = parseUntil(until);
        if (documentId == null || documentId.isBlank()) {
            throw ApiException.unprocessable("template_not_found", "documentId is required");
        }
        // A0 : enveloppe seule. Le fichier et le gabarit seront traités à partir de A1.
        return AuthenticityCheckResponse.empty(documentId.trim(), stoppedAt);
    }

    private void requireEnabled() {
        if (!properties.authenticity().labEnabled()) {
            throw ApiException.notFound("Not found");
        }
    }

    private static String parseUntil(String until) {
        if (until == null || until.isBlank()) {
            return "decide";
        }
        String stage = until.trim().toLowerCase(Locale.ROOT);
        if (!STAGES.contains(stage)) {
            throw ApiException.validation("Invalid until", List.of(new ErrorDetail("until", "invalid")));
        }
        return stage;
    }
}
