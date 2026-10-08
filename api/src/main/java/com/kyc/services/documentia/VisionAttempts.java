package com.kyc.services.documentia;

import com.kyc.dto.documentia.DocumentParse;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.ports.VisionDocumentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Au plus deux appels. Le second échec distingue un fournisseur injoignable d'un JSON hors contrat. */
public final class VisionAttempts {

    private static final Logger log = LoggerFactory.getLogger(VisionAttempts.class);

    private VisionAttempts() {}

    public record Success(String raw, DocumentParse parsed) {}

    public record Result(Success success, String failureCode) {}

    public static Result call(
            VisionDocumentPort port, DocumentAnalysisParser parser, byte[] image, String mediaType, String prompt) {
        String failureCode = "provider_unavailable";
        for (int attempt = 1; attempt <= 2; attempt++) {
            long start = System.nanoTime();
            try {
                String raw = port.complete(image, mediaType, prompt);
                DocumentParse parsed = parser.parse(raw);
                log.info(
                        "document-ia vision completed attempt={} durationMs={} code={} providerCalled=true",
                        attempt,
                        elapsed(start),
                        parsed.document().classification().code());
                return new Result(new Success(raw, parsed), null);
            } catch (ProviderUnavailableException e) {
                failureCode = "provider_unavailable";
                log.info(
                        "document-ia vision failed attempt={} durationMs={} code=provider_unavailable",
                        attempt,
                        elapsed(start));
            } catch (InvalidModelJsonException e) {
                failureCode = "invalid_model_json";
                log.info(
                        "document-ia vision failed attempt={} durationMs={} code=invalid_model_json reason={}",
                        attempt,
                        elapsed(start),
                        e.getMessage());
            }
        }
        return new Result(null, failureCode);
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
