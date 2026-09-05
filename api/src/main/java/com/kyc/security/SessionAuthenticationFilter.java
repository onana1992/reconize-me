package com.kyc.security;

import com.kyc.services.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final List<String> PROTECTED = List.of("/v1/console/**", "/v1/account/logout");

    private final SessionService sessionService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public SessionAuthenticationFilter(
            SessionService sessionService, RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.sessionService = sessionService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = normalized(request);
        return PROTECTED.stream().noneMatch(pattern -> PATHS.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String raw = cookie(request);
        var principal = sessionService.authenticate(raw);
        if (principal.isEmpty()) {
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("missing session"));
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal.get(), null, List.of(new SimpleGrantedAuthority("ROLE_CONSOLE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private static String cookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String normalized(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path;
    }
}
