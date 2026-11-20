package com.kyc.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * En local, si le SSL Postgres vers Neon ne répond pas, remplace l'URL JDBC
 * par le pont WebSocket. Sur Render le port 5432 répond : l'URL reste directe.
 */
public class NeonBridgeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if ("true".equalsIgnoreCase(System.getenv("RENDER"))) {
            return;
        }
        if ("false".equalsIgnoreCase(environment.getProperty("kyc.datasource.neon-bridge"))) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        String host = NeonLocalBridge.hostOf(url);
        if (host == null || !host.endsWith(".neon.tech")) {
            return;
        }
        if (NeonLocalBridge.directSslReady(host)) {
            return;
        }
        int port = NeonLocalBridge.ensureStarted(host);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", NeonLocalBridge.localJdbcUrl(url, port));
        environment.getPropertySources().addFirst(new MapPropertySource("kycNeonBridge", props));
        System.err.println("Neon: port 5432 sans réponse, pont WebSocket sur 127.0.0.1:" + port);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
