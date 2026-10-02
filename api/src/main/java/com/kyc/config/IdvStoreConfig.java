package com.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.AwsBiometricAi;
import com.kyc.adapters.AwsDocumentAi;
import com.kyc.adapters.FilesystemObjectStorage;
import com.kyc.adapters.InMemoryHostedTokenStore;
import com.kyc.adapters.QcAnalyzeIdMapper;
import com.kyc.adapters.RedisHostedTokenStore;
import com.kyc.adapters.RekognitionCompareFacesClient;
import com.kyc.adapters.StubBiometricAi;
import com.kyc.adapters.StubDocumentAi;
import com.kyc.adapters.TextractAnalyzeIdClient;
import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.ports.ProviderUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @Qualifier("stubDocumentAi")
    public DocumentAiPort stubDocumentAi() {
        return new StubDocumentAi();
    }

    @Bean
    @Qualifier("stubBiometricAi")
    public BiometricAiPort stubBiometricAi() {
        return new StubBiometricAi();
    }

    @Bean
    public QcAnalyzeIdMapper qcAnalyzeIdMapper() {
        return new QcAnalyzeIdMapper();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kyc.aws", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(AnalyzeIdClient.class)
    public AnalyzeIdClient textractAnalyzeIdClient(KycProperties properties, ObjectMapper objectMapper) {
        return new TextractAnalyzeIdClient(properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "kyc.aws", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(CompareFacesClient.class)
    public CompareFacesClient rekognitionCompareFacesClient(KycProperties properties) {
        return new RekognitionCompareFacesClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyzeIdClient.class)
    public AnalyzeIdClient disabledAnalyzeIdClient() {
        return image -> {
            throw new ProviderUnavailableException("AWS AnalyzeID disabled (kyc.aws.enabled=false)");
        };
    }

    @Bean
    @ConditionalOnMissingBean(CompareFacesClient.class)
    public CompareFacesClient disabledCompareFacesClient() {
        return (document, selfie) -> {
            throw new ProviderUnavailableException("AWS CompareFaces disabled (kyc.aws.enabled=false)");
        };
    }

    @Bean
    @Qualifier("liveDocumentAi")
    public DocumentAiPort liveDocumentAi(
            AnalyzeIdClient analyzeIdClient, QcAnalyzeIdMapper mapper, ObjectMapper objectMapper) {
        return new AwsDocumentAi(analyzeIdClient, mapper, objectMapper);
    }

    @Bean
    @Qualifier("liveBiometricAi")
    public BiometricAiPort liveBiometricAi(CompareFacesClient compareFacesClient) {
        return new AwsBiometricAi(compareFacesClient);
    }
}
