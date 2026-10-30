package com.kyc.adapters;

import com.kyc.ports.DocumentAiPort;
import com.kyc.services.DocumentAnalysisBuffer;
import com.kyc.services.DocumentIaLabService;
import com.kyc.services.documentia.DocumentLiveView;
import com.kyc.services.documentia.SchemaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pipeline V3–V7 projeté vers le signal documentaire. Le fournisseur est vision_llm. */
public class VisionDocumentAi implements DocumentAiPort {

    private static final Logger log = LoggerFactory.getLogger(VisionDocumentAi.class);

    private final DocumentIaLabService lab;
    private final DocumentAnalysisBuffer buffer;
    private final SchemaRegistry registry;
    private final String modelId;

    public VisionDocumentAi(
            DocumentIaLabService lab, DocumentAnalysisBuffer buffer, SchemaRegistry registry, String modelId) {
        this.lab = lab;
        this.buffer = buffer;
        this.registry = registry;
        this.modelId = modelId == null || modelId.isBlank() ? "fake" : modelId;
    }

    @Override
    public DocumentSignals analyze(byte[] documentImage, String sandboxScenario) {
        DocumentLiveView view = new DocumentLiveView(lab.decideImage(documentImage, "image/jpeg"), modelId);
        buffer.offer(view);
        log.info(
                "document-ia live accepted={} stoppedAt={} providerCalled={}",
                view.accepted(),
                view.response().stoppedAt(),
                view.response().providerCalled());
        return view.signals(registry);
    }

    public DocumentLiveView analyzeCapture(byte[] documentImage, String side) {
        return analyzeCapture(documentImage, side, null);
    }

    public DocumentLiveView analyzeCapture(byte[] documentImage, String side, String documentCode) {
        DocumentLiveView view =
                new DocumentLiveView(lab.decideImage(documentImage, "image/jpeg", side, documentCode), modelId);
        log.info(
                "document-ia live accepted={} stoppedAt={} providerCalled={} side={}",
                view.accepted(),
                view.response().stoppedAt(),
                view.response().providerCalled(),
                side);
        return view;
    }

    public static final String QUEBEC_BACK_BARCODE =
            """
            {"detection":"DOCUMENT_PRESENT","classification":{"code":"QUEBEC_DRIVER_LICENSE","version":"2024","side":"BACK","confidence":0.97},"fields":[{"field":"barcode","value":"PRESENT","confidence":0.96,"source":"VISUAL_TEXT"}]}""";

    /** Verso du permis du Québec déjà identifié au recto. Le code-barres tient lieu de lecture. */
    public DocumentLiveView acceptQuebecBack(byte[] documentImage) {
        DocumentLiveView view = new DocumentLiveView(lab.decideSupplied(documentImage, "BACK", QUEBEC_BACK_BARCODE), "pdf417");
        log.info(
                "document-ia live accepted={} stoppedAt={} providerCalled={} side=BACK model=pdf417",
                view.accepted(),
                view.response().stoppedAt(),
                view.response().providerCalled());
        return view;
    }
}
