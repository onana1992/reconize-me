package com.kyc.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorBody(
        String code,
        String message,
        @JsonProperty("request_id") String requestId,
        List<ErrorDetail> details) {

    public ErrorBody(String code, String message, String requestId) {
        this(code, message, requestId, List.of());
    }
}
