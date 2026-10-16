package com.kyc.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Quand {@code kyc.redis.enabled=false}, exclut l'auto-config Redis pour basculer
 * sur les stores mémoire et laisser le health UP sans daemon Redis.
 */
public class OptionalRedisEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty("kyc.redis.enabled", Boolean.class, true)) {
            return;
        }
        Set<String> excludes = new LinkedHashSet<>();
        String existing = environment.getProperty("spring.autoconfigure.exclude");
        if (existing != null && !existing.isBlank()) {
            Arrays.stream(existing.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(excludes::add);
        }
        excludes.add(RedisAutoConfiguration.class.getName());
        excludes.add(RedisRepositoriesAutoConfiguration.class.getName());
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.autoconfigure.exclude", String.join(",", excludes));
        props.put("management.health.redis.enabled", "false");
        environment.getPropertySources().addFirst(new MapPropertySource("kycRedisOptional", props));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
