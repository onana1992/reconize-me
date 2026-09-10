package com.kyc.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientIps {

    private static final int MAX_LENGTH = 45;

    private ClientIps() {}

    public static String current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        return from(servlet.getRequest());
    }

    public static String from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip;
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            ip = comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
        } else {
            ip = request.getRemoteAddr();
        }
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }
}
