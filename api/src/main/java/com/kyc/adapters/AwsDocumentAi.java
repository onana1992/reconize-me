package com.kyc.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsDocumentAi implements DocumentAiPort {

    private static final Logger log = LoggerFactory.getLogger(AwsDocumentAi.class);

    private final AnalyzeIdClient client;
    private final QcAnalyzeIdMapper mapper;
    private final ObjectMapper objectMapper;

    public AwsDocumentAi(AnalyzeIdClient client, QcAnalyzeIdMapper mapper, ObjectMapper objectMapper) {
        this.client = client;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentSignals analyze(byte[] documentImage, String sandboxScenario) {
        // sandboxScenario is ignored in live (RG-M5-02).
        long start = System.nanoTime();
        try {
            String raw = client.analyzeId(documentImage);
            JsonNode node = objectMapper.readTree(raw);
            DocumentSignals signals = mapper.map(node);
            log.info("AnalyzeID completed durationMs={}", (System.nanoTime() - start) / 1_000_000L);
            return signals;
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AnalyzeID failed durationMs={}", (System.nanoTime() - start) / 1_000_000L);
            throw new ProviderUnavailableException("Textract AnalyzeID unavailable", e);
        }
    }
}
