package com.kyc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.web.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ApiErrors.write(
                request,
                response,
                objectMapper,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                sessionPath(request) ? "Invalid or missing session" : "Invalid or missing API key");
    }

    private static boolean sessionPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/v1/console") || path.startsWith("/v1/account");
    }
}
