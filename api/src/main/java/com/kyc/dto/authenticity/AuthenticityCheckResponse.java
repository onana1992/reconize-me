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
        RectifyBlock rectify,
        List<ProjectedBoxBlock> project,
        List<FixedBlock> fixed,
        List<PositionBlock> positions) {

    public static AuthenticityCheckResponse of(
            String documentId,
            String stoppedAt,
            String verdict,
            List<String> codes,
            RectifyBlock rectify,
            List<ProjectedBoxBlock> project,
            List<FixedBlock> fixed,
            List<PositionBlock> positions) {
        return new AuthenticityCheckResponse(
                "template-1", stoppedAt, documentId, verdict, codes, rectify, project, fixed, positions);
    }
}
