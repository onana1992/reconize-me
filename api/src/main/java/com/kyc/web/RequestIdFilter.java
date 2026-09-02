package com.kyc.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = RequestIds.resolve(request.getHeader(RequestIds.HEADER));
        request.setAttribute(RequestIds.ATTRIBUTE, requestId);
        MDC.put("request_id", requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
        }
    }
}
