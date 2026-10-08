package com.kyc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.AwsBiometricAi;
import com.kyc.adapters.AwsClientCredentials;
import com.kyc.adapters.FilesystemObjectStorage;
import com.kyc.adapters.InMemoryHostedTokenStore;
import com.kyc.adapters.QcAnalyzeIdMapper;
import com.kyc.adapters.RedisHostedTokenStore;
import com.kyc.adapters.RekognitionCompareFacesClient;
import com.kyc.adapters.S3ObjectStorage;
import com.kyc.adapters.StubBiometricAi;
import com.kyc.adapters.StubDocumentAi;
import com.kyc.adapters.TextractAnalyzeIdClient;
import com.kyc.adapters.VisionDocumentAi;
import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.services.DocumentAnalysisBuffer;
import com.kyc.services.DocumentIaLabService;
import com.kyc.services.documentia.SchemaRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.services.s3.S3Client;

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

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "kyc.aws.s3", name = "enabled", havingValue = "true")
    public ObjectStoragePort s3ObjectStorage(
            KycProperties properties, @Value("${kyc.public-api-base-url:http://localhost:8080}") String publicApiBase) {
        KycProperties.Aws.S3 s3 = properties.aws().s3();
        if (s3.bucket() == null || s3.bucket().isBlank()) {
            throw new IllegalStateException("kyc.aws.s3.bucket is required when kyc.aws.s3.enabled=true");
        }
        S3Client client = S3Client.builder()
                .region(AwsClientCredentials.region(s3.region(), properties.aws().region()))
                .credentialsProvider(AwsClientCredentials.require(
                        s3.accessKeyId(), s3.secretAccessKey(), "kyc.aws.s3"))
                .build();
        return new S3ObjectStorage(client, s3.bucket(), s3.keyPrefix(), publicApiBase, properties.ipHashPepper());
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    public ObjectStoragePort filesystemObjectStorage(
            KycProperties properties, @Value("${kyc.public-api-base-url:http://localhost:8080}") String publicApiBase) {
        return new FilesystemObjectStorage(properties, publicApiBase);
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
    public VisionDocumentAi liveDocumentAi(
            DocumentIaLabService lab,
            DocumentAnalysisBuffer buffer,
            SchemaRegistry registry,
            @Value("${kyc.document-ia.provider:fake}") String provider,
            @Value("${kyc.document-ia.openai-model:gpt-4.1-mini}") String model) {
        String modelId = "openai".equals(provider) ? model : provider;
        return new VisionDocumentAi(lab, buffer, registry, modelId);
    }

    @Bean
    @Qualifier("liveBiometricAi")
    public BiometricAiPort liveBiometricAi(CompareFacesClient compareFacesClient) {
        return new AwsBiometricAi(compareFacesClient);
    }
}
