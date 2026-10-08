package com.kyc.adapters;

import com.kyc.ports.ProviderUnavailableException;
import com.kyc.ports.VisionDocumentPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Réponse figée, sans réseau. Le prompt vient du catalogue, pas d'une branche par document. */
public class FakeVisionDocument implements VisionDocumentPort {

    private final String fixture;

    public FakeVisionDocument() {
        try {
            fixture = new ClassPathResource("document-ia/quebec-driver-license.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Vision fixture is missing", e);
        }
    }

    @Override
    public String complete(byte[] image, String mediaType, String prompt) {
        if (image == null || image.length == 0 || prompt == null || prompt.isBlank()) {
            throw new ProviderUnavailableException("vision_unavailable");
        }
        return fixture;
    }
}
