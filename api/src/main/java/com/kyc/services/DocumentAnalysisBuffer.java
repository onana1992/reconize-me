package com.kyc.services;

import com.kyc.services.documentia.DocumentLiveView;
import org.springframework.stereotype.Component;

/** Relie l'adaptateur vision au flow, le temps d'une décision sur le fil courant. */
@Component
public class DocumentAnalysisBuffer {

    private final ThreadLocal<DocumentLiveView> current = new ThreadLocal<>();

    public void offer(DocumentLiveView view) {
        current.set(view);
    }

    public DocumentLiveView take() {
        try {
            return current.get();
        } finally {
            current.remove();
        }
    }
}
