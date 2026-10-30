package com.kyc.services.documentia;

import com.kyc.dto.documentia.DocumentParse;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.ports.VisionDocumentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Un fournisseur injoignable est retenté une fois. Un JSON hors contrat ne l'est pas. */
public final class VisionAttempts {

    private static final Logger log = LoggerFactory.getLogger(VisionAttempts.class);

    private VisionAttempts() {}

    public record Success(String raw, DocumentParse parsed) {}

    public record Result(Success success, String failureCode) {}

    public static Result call(
            VisionDocumentPort port, DocumentAnalysisParser parser, byte[] image, String mediaType, String prompt) {
        return call(port, parser, image, mediaType, prompt, "high");
    }

    public static Result call(
            VisionDocumentPort port,
            DocumentAnalysisParser parser,
            byte[] image,
            String mediaType,
            String prompt,
            String detail) {
        String failureCode = "provider_unavailable";
        String chosen = "low".equals(detail) ? "low" : "high";
        for (int attempt = 1; attempt <= 2; attempt++) {
            long start = System.nanoTime();
            try {
                String raw = port.complete(image, mediaType, prompt, chosen);
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
                log.info(
                        "document-ia vision failed attempt={} durationMs={} code=invalid_model_json reason={}",
                        attempt,
                        elapsed(start),
                        e.getMessage());
                return new Result(null, "invalid_model_json");
            }
        }
        return new Result(null, failureCode);
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
