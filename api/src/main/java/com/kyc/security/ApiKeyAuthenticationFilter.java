package com.kyc.security;

import com.kyc.services.ApiKeyAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final List<String> PUBLIC = List.of(
            "/v1/health",
            "/v1/flow/**",
            "/v1/account/**",
            "/v1/console/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**");

    private final ApiKeyAuthenticator apiKeyAuthenticator;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public ApiKeyAuthenticationFilter(
            ApiKeyAuthenticator apiKeyAuthenticator, RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.apiKeyAuthenticator = apiKeyAuthenticator;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        String normalized = path;
        return PUBLIC.stream().anyMatch(pattern -> PATHS.match(pattern, normalized));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("missing bearer"));
            return;
        }
        String rawKey = header.substring("Bearer ".length()).trim();
        var principal = apiKeyAuthenticator.authenticate(rawKey);
        if (principal.isEmpty()) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("invalid api key"));
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal.get(), null, List.of(new SimpleGrantedAuthority("ROLE_TENANT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
