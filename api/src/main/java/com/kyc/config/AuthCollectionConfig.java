package com.kyc.config;

import com.kyc.adapters.InMemoryFaceCollectionClient;
import com.kyc.adapters.RekognitionFaceCollectionClient;
import com.kyc.ports.FaceCollectionClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auth face collection. Real Rekognition when AWS is enabled; in-memory fake otherwise. */
@Configuration
public class AuthCollectionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "kyc.aws", name = "enabled", havingValue = "true")
    public FaceCollectionClient rekognitionFaceCollectionClient(KycProperties properties) {
        return new RekognitionFaceCollectionClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean(FaceCollectionClient.class)
    public InMemoryFaceCollectionClient inMemoryFaceCollectionClient() {
        return new InMemoryFaceCollectionClient();
    }
}
