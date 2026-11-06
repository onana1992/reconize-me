package com.kyc.ports;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JPEG d'origine vers le JSON du service anti-spoof. Le banc n'écrit aucun dossier.
 */
public interface DocumentAuthenticityPort {

    JsonNode analyze(byte[] jpeg);
}
