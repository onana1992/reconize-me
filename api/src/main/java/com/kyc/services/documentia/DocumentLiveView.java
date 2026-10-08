package com.kyc.services.documentia;

import com.kyc.dto.documentia.DocumentDecision;
import com.kyc.dto.documentia.DocumentIaAnalysisResponse;
import com.kyc.dto.documentia.DocumentImageQuality;
import com.kyc.dto.documentia.MrzReport;
import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.enums.SignalOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.ports.DocumentAiPort.DocumentSignals;
import com.kyc.services.IdvDecisionEngine;
import com.kyc.services.IdvDecisionEngine.Signal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Résultat du pipeline jusqu'à la décision, prêt à projeter et à persister. */
public final class DocumentLiveView {

    private final DocumentIaAnalysisResponse response;
    private final String modelId;

    public DocumentLiveView(DocumentIaAnalysisResponse response, String modelId) {
        this.response = response;
        this.modelId = modelId;
    }

    public DocumentIaAnalysisResponse response() {
        return response;
    }

    public String modelId() {
        return modelId;
    }

    public boolean accepted() {
        return response.parsed() instanceof ParsedDocument;
    }

    public IdvDecisionEngine.Result result() {
        DocumentDecision decision = response.decision() instanceof DocumentDecision value ? value : null;
        VerificationDecision verdict = VerificationDecision.REVIEW;
        List<String> reasons = List.of("provider_unavailable");
        if (decision != null) {
            reasons = decision.reasons() == null ? List.of() : decision.reasons();
            if (decision.verificationDecision() != null) {
                verdict = VerificationDecision.valueOf(decision.verificationDecision());
            }
        }
        List<Signal> signals = new ArrayList<>();
        for (String reason : reasons) {
            signals.add(new Signal(reason, SignalOutcome.FAIL, null));
        }
        return new IdvDecisionEngine.Result(verdict, reasons, List.copyOf(signals), identity());
    }

    public DocumentSignals signals(SchemaRegistry registry) {
        if (!(response.parsed() instanceof ParsedDocument parsed)) {
            return new DocumentSignals(
                    "unknown", "ZZ", null, false, false, false, null, null, null, null, null, "vision_llm");
        }
        SchemaEdition edition = registry.edition(response.schemaVersionId()).orElse(null);
        DocumentImageQuality quality = response.quality() instanceof DocumentImageQuality value ? value : null;
        boolean readable = quality == null || quality.readable();
        String capture = quality == null ? null : quality.reason();
        return DocumentValidator.prepare(
                        edition,
                        parsed,
                        fields(),
                        validation(),
                        response.mrz() instanceof MrzReport mrz ? mrz : null,
                        readable,
                        capture)
                .signals();
    }

    @SuppressWarnings("unchecked")
    public List<NormalizedField> fields() {
        return response.fields() instanceof List<?> list ? (List<NormalizedField>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<ValidationIssue> validation() {
        return response.validation() instanceof List<?> list ? (List<ValidationIssue>) list : List.of();
    }

    private Map<String, Object> identity() {
        if (response.parsed() instanceof ParsedDocument parsed && parsed.extractedIdentity() != null) {
            return parsed.extractedIdentity();
        }
        return Map.of();
    }
}
