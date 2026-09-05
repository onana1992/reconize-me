package com.kyc.web;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ErrorDetail> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<ErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static ApiException validation(String message, List<ErrorDetail> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_error", message, details);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException dependencyUnavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "dependency_unavailable", message);
    }

    public static ApiException gone(String code, String message) {
        return new ApiException(HttpStatus.GONE, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException tooManyRequests() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts, try again later");
    }

    public static ApiException invalidOrExpiredToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_or_expired_token", "Invalid or expired token");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
