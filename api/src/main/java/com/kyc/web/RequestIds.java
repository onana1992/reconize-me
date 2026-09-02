package com.kyc.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "requestId";

    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private RequestIds() {}

    public static String generate() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String resolve(String incoming) {
        if (incoming != null && CLIENT_ID.matcher(incoming.trim()).matches()) {
            return incoming.trim();
        }
        return generate();
    }

    public static String current(HttpServletRequest request) {
        Object attribute = request.getAttribute(ATTRIBUTE);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return resolve(request.getHeader(HEADER));
    }
}
