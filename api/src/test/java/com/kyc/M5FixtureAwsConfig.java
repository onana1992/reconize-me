package com.kyc;

import com.kyc.ports.AnalyzeIdClient;
import com.kyc.ports.CompareFacesClient;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

@TestConfiguration
public class M5FixtureAwsConfig {

    static String fixtureJson() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("fixtures/analyzeid-qc.json").getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Bean
    @Primary
    AnalyzeIdClient fixtureAnalyzeIdClient() {
        return image -> fixtureJson();
    }

    @Bean
    @Primary
    CompareFacesClient fixtureCompareFacesClient() {
        return (document, selfie) -> 0.96;
    }
}
