package com.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.FakeVisionDocument;
import com.kyc.adapters.OpenAiVisionDocument;
import com.kyc.adapters.UnavailableVisionDocument;
import com.kyc.ports.VisionDocumentPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentIaVisionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "kyc.document-ia", name = "provider", havingValue = "fake", matchIfMissing = true)
    public VisionDocumentPort fakeVisionDocument() {
        return new FakeVisionDocument();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kyc.document-ia", name = "provider", havingValue = "unavailable")
    public UnavailableVisionDocument unavailableVisionDocument() {
        return new UnavailableVisionDocument();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kyc.document-ia", name = "provider", havingValue = "openai")
    public VisionDocumentPort openAiVisionDocument(
            @Value("${kyc.document-ia.openai-api-key:}") String apiKey,
            @Value("${kyc.document-ia.openai-model:gpt-4.1-mini}") String model,
            ObjectMapper objectMapper) {
        return new OpenAiVisionDocument(apiKey, model, objectMapper);
    }
}
