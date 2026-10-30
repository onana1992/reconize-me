package com.kyc.ports;

/**
 * Image plus catalogue actif vers le JSON brut du modèle. Le banc n'utilise pas Textract.
 */
public interface VisionDocumentPort {

    String complete(byte[] image, String mediaType, String prompt);

    /** {@code low} envoie une seule vignette. {@code high} reste le défaut. */
    default String complete(byte[] image, String mediaType, String prompt, String detail) {
        return complete(image, mediaType, prompt);
    }
}
