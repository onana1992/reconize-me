package com.kyc.dto.authenticity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Enveloppe du banc authenticité. Les blocs absents restent {@code null} : Jackson les écrit pour que Swagger
 * montre la forme.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AuthenticityCheckResponse(
        String pipeline,
        String stoppedAt,
        String documentId,
        String verdict,
        List<String> codes,
        Object rectify,
        Object fixed,
        Object positions) {

    public static AuthenticityCheckResponse empty(String documentId, String stoppedAt) {
        return new AuthenticityCheckResponse(
                "template-1", stoppedAt, documentId, null, List.of(), null, null, null);
    }
}
