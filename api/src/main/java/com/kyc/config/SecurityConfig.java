package com.kyc.config;

import com.kyc.security.ApiKeyAuthenticationFilter;
import com.kyc.security.RestAccessDeniedHandler;
import com.kyc.security.RestAuthenticationEntryPoint;
import com.kyc.security.SessionAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-Id", "Set-Cookie"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            SessionAuthenticationFilter sessionAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/v1/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/account/signup")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/account/verify")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/account/verify/resend")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/account/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/account/password/forgot")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/account/password/reset")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/account/invites/accept")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/account/invites")
                        .permitAll()
                        .requestMatchers("/v1/account/**", "/v1/console/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(sessionAuthenticationFilter, ApiKeyAuthenticationFilter.class);
        return http.build();
    }
}
