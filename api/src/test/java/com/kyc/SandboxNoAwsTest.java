package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.kyc.adapters.StubBiometricAi;
import com.kyc.adapters.StubDocumentAi;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.DocumentAiPort;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SandboxNoAwsTest.CountingAwsConfig.class)
class SandboxNoAwsTest {

    private static final String KEY = "ky_test_noaws01";
    private static final AtomicInteger ANALYZE_CALLS = new AtomicInteger();
    private static final AtomicInteger COMPARE_CALLS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    @Qualifier("stubDocumentAi")
    private DocumentAiPort stubDocumentAi;

    @Autowired
    @Qualifier("stubBiometricAi")
    private BiometricAiPort stubBiometricAi;

    @Test
    void sandboxUsesStubsAndNeverCallsAwsClients() throws Exception {
        ANALYZE_CALLS.set(0);
        COMPARE_CALLS.set(0);
        assertInstanceOf(StubDocumentAi.class, stubDocumentAi);
        assertInstanceOf(StubBiometricAi.class, stubBiometricAi);

        IdvSupport.seedBearer(organizations, integrations, apiKeys, passwordEncoder, KEY, "NoAws Co", "noaws-co");
        var created = IdvSupport.create(mockMvc, KEY, "{}");
        String token = IdvSupport.token(created);
        IdvSupport.flow(mockMvc, token);
        IdvSupport.acceptConsent(mockMvc, token);
        IdvSupport.captureDocument(mockMvc, token, IdvSupport.goodJpeg());
        var done = IdvSupport.captureSelfie(mockMvc, token, IdvSupport.goodJpeg());
        assertEquals("approved", done.path("status").asText());
        assertEquals(0, ANALYZE_CALLS.get());
        assertEquals(0, COMPARE_CALLS.get());
    }

    @TestConfiguration
    static class CountingAwsConfig {
        @Bean
        @Primary
        com.kyc.ports.AnalyzeIdClient countingAnalyzeIdClient() {
            return image -> {
                ANALYZE_CALLS.incrementAndGet();
                throw new com.kyc.ports.ProviderUnavailableException("should not be called");
            };
        }

        @Bean
        @Primary
        com.kyc.ports.CompareFacesClient countingCompareFacesClient() {
            return (document, selfie) -> {
                COMPARE_CALLS.incrementAndGet();
                throw new com.kyc.ports.ProviderUnavailableException("should not be called");
            };
        }
    }
}
