package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.util.List;
import java.util.Map;

final class MetadataValidator {

    static final int MAX_BYTES = 4096;
    static final int MAX_DEPTH = 2;

    private MetadataValidator() {}

    static Map<String, Object> requireValid(ObjectMapper objectMapper, Map<String, Object> metadata) {
        Map<String, Object> value = metadata == null ? Map.of() : metadata;
        if (depth(value) > MAX_DEPTH) {
            throw ApiException.validation("metadata is invalid", List.of(new ErrorDetail("metadata", "depth")));
        }
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw ApiException.validation("metadata is invalid", List.of(new ErrorDetail("metadata", "type")));
        }
        if (json.length > MAX_BYTES) {
            throw ApiException.validation("metadata is invalid", List.of(new ErrorDetail("metadata", "size")));
        }
        return value;
    }

    static String toJson(ObjectMapper objectMapper, Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            throw ApiException.validation("metadata is invalid", List.of(new ErrorDetail("metadata", "type")));
        }
    }

    private static int depth(Object value) {
        if (value instanceof Map<?, ?> map) {
            int max = 0;
            for (Object nested : map.values()) {
                max = Math.max(max, depth(nested));
            }
            return 1 + max;
        }
        if (value instanceof List<?> list) {
            int max = 0;
            for (Object nested : list) {
                max = Math.max(max, depth(nested));
            }
            return 1 + max;
        }
        return 0;
    }
}
