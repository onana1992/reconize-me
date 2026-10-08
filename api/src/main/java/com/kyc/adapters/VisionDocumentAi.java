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
        DocumentLiveView view = new DocumentLiveView(lab.decideImage(documentImage, "image/jpeg", side), modelId);
        log.info(
                "document-ia live accepted={} stoppedAt={} providerCalled={} side={}",
                view.accepted(),
                view.response().stoppedAt(),
                view.response().providerCalled(),
                side);
        return view;
    }
}
