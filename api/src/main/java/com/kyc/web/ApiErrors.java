package com.kyc.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ApiErrors {

    private ApiErrors() {}

    public static ErrorResponse body(String code, String message, HttpServletRequest request) {
        return ErrorResponse.of(code, message, RequestIds.current(request));
    }

    public static ErrorResponse body(
            String code, String message, HttpServletRequest request, List<ErrorDetail> details) {
        return ErrorResponse.of(code, message, RequestIds.current(request), details);
    }

    public static ResponseEntity<ErrorResponse> entity(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .header(RequestIds.HEADER, RequestIds.current(request))
                .body(body(code, message, request));
    }

    public static ResponseEntity<ErrorResponse> entity(
            HttpStatus status, String code, String message, HttpServletRequest request, List<ErrorDetail> details) {
        return ResponseEntity.status(status)
                .header(RequestIds.HEADER, RequestIds.current(request))
                .body(body(code, message, request, details));
    }

    public static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String requestId = RequestIds.current(request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(RequestIds.HEADER, requestId);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message, requestId));
    }
}
