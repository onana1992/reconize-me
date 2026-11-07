package com.kyc.ports;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/**
 * JPEG d'origine vers le JSON du service anti-spoof.
 */
public interface DocumentAuthenticityPort {

    record AuthenticityRequest(UUID verificationId, String side, String documentCode, JsonNode regions) {}

    JsonNode analyze(byte[] jpeg);

    default JsonNode analyze(byte[] jpeg, AuthenticityRequest request) {
        return analyze(jpeg);
    }
}
