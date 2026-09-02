package com.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.InMemoryHostedTokenStore;
import com.kyc.adapters.RedisHostedTokenStore;
import com.kyc.ports.HostedTokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class HostedTokenStoreConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public HostedTokenStore redisHostedTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new RedisHostedTokenStore(redis, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(HostedTokenStore.class)
    public HostedTokenStore inMemoryHostedTokenStore() {
        return new InMemoryHostedTokenStore();
    }
}
