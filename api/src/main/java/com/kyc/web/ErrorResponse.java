package com.kyc.web;

public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(String code, String message, String requestId) {
        return new ErrorResponse(new ErrorBody(code, message, requestId));
    }

    public static ErrorResponse of(String code, String message, String requestId, java.util.List<ErrorDetail> details) {
        return new ErrorResponse(new ErrorBody(code, message, requestId, details));
    }
}
