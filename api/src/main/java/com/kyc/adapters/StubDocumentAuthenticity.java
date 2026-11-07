package com.kyc.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Clés de test, et live tant que {@code kyc.anti-spoof.enabled} est faux. Aucun HTTP.
 */
public final class StubDocumentAuthenticity {

    public static final String ENGINE_VERSION = "stub";

    private static final List<String> ALERTS = List.of(
            "splicing",
            "text_alteration",
            "compression_inconsistency",
            "screen_replay",
            "printed_copy");

    private StubDocumentAuthenticity() {}

    public static JsonNode assessment(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("engine_version", ENGINE_VERSION);
        node.put("fusion_version", ENGINE_VERSION);
        node.put("forensic_usable", true);
        node.put("authenticity_score", 1.0);
        node.put("tampering_score", 0.0);
        node.put("presentation_score", 0.0);
        ArrayNode alerts = node.putArray("alerts");
        for (String code : ALERTS) {
            ObjectNode alert = alerts.addObject();
            alert.put("code", code);
            alert.put("score", 0.0);
            alert.putArray("regions");
        }
        return node;
    }
}
