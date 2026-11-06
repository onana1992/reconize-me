package com.kyc.dto.antispoof;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.kyc.services.IdvDecisionEngine.AuthenticityVerdict;

public record AntiSpoofLabResponse(
        String pipeline,
        boolean providerCalled,
        String engineVersion,
        JsonNode assessment,
        AuthenticityVerdict decision,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code) {

    public static AntiSpoofLabResponse assessed(
            String engineVersion, JsonNode assessment, AuthenticityVerdict decision) {
        return new AntiSpoofLabResponse("auth-1", true, engineVersion, assessment, decision, null);
    }

    public static AntiSpoofLabResponse unavailable(AuthenticityVerdict decision) {
        return new AntiSpoofLabResponse("auth-1", true, null, null, decision, "authenticity_unavailable");
    }
}
