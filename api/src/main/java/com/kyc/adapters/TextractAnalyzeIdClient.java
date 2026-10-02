package com.kyc.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kyc.config.KycProperties;
import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.ProviderUnavailableException;
import java.util.List;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeIdRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeIdResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.IdentityDocument;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;

public class TextractAnalyzeIdClient implements AnalyzeIdClient {

    private final TextractClient textract;
    private final ObjectMapper objectMapper;

    public TextractAnalyzeIdClient(KycProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.textract = TextractClient.builder()
                .region(Region.of(properties.aws().region()))
                .build();
    }

    TextractAnalyzeIdClient(TextractClient textract, ObjectMapper objectMapper) {
        this.textract = textract;
        this.objectMapper = objectMapper;
    }

    @Override
    public String analyzeId(byte[] documentImage) {
        try {
            AnalyzeIdResponse response = textract.analyzeID(AnalyzeIdRequest.builder()
                    .documentPages(Document.builder().bytes(SdkBytes.fromByteArray(documentImage)).build())
                    .build());
            return toJson(response.identityDocuments());
        } catch (RuntimeException e) {
            throw new ProviderUnavailableException("Textract AnalyzeID call failed", e);
        }
    }

    private String toJson(List<IdentityDocument> documents) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode docs = root.putArray("IdentityDocuments");
        for (IdentityDocument document : documents) {
            ObjectNode doc = docs.addObject();
            ArrayNode fields = doc.putArray("IdentityDocumentFields");
            for (IdentityDocumentField field : document.identityDocumentFields()) {
                ObjectNode item = fields.addObject();
                item.putObject("Type").put("Text", field.type() == null ? "" : nullToEmpty(field.type().text()));
                item.putObject("ValueDetection")
                        .put("Text", field.valueDetection() == null ? "" : nullToEmpty(field.valueDetection().text()));
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new ProviderUnavailableException("Failed to encode AnalyzeID response", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
