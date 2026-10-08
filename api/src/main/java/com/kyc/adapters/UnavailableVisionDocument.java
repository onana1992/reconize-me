package com.kyc.adapters;

import com.kyc.ports.ProviderUnavailableException;
import com.kyc.ports.VisionDocumentPort;
import java.util.concurrent.atomic.AtomicInteger;

/** Simule une clé absente ou un délai. Chaque appel compte, pour vérifier les deux tentatives. */
public class UnavailableVisionDocument implements VisionDocumentPort {

    private final AtomicInteger attempts = new AtomicInteger();

    public int attempts() {
        return attempts.get();
    }

    public void reset() {
        attempts.set(0);
    }

    @Override
    public String complete(byte[] image, String mediaType, String prompt) {
        attempts.incrementAndGet();
        throw new ProviderUnavailableException("vision_unavailable");
    }
}
