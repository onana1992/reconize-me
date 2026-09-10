package com.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.FilesystemObjectStorage;
import com.kyc.adapters.InMemoryHostedTokenStore;
import com.kyc.adapters.RedisHostedTokenStore;
import com.kyc.adapters.StubBiometricAi;
import com.kyc.adapters.StubDocumentAi;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.ObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class IdvStoreConfig {

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

    @Bean
    public ObjectStoragePort objectStoragePort(KycProperties properties) {
        return new FilesystemObjectStorage(properties, "http://localhost:8080");
    }

    @Bean
    public DocumentAiPort documentAiPort() {
        return new StubDocumentAi();
    }

    @Bean
    public BiometricAiPort biometricAiPort() {
        return new StubBiometricAi();
    }
}
