package com.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.InMemoryConsoleSessionStore;
import com.kyc.adapters.RedisConsoleSessionStore;
import com.kyc.ports.ConsoleSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ConsoleSessionStoreConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public ConsoleSessionStore redisConsoleSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new RedisConsoleSessionStore(redis, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ConsoleSessionStore.class)
    public ConsoleSessionStore inMemoryConsoleSessionStore() {
        return new InMemoryConsoleSessionStore();
    }
}
