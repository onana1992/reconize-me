package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.enums.IdentityScreeningEffect;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.services.IdvDecisionEngine.DecisionProfile;
import com.kyc.services.aml.Aml2Decider;
import com.kyc.services.aml.DeterministicAmlEngine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityScreeningServiceTest {

    private IdvPolicyService policies;
    private DeterministicAmlEngine engine;
    private SanctionsMatchPort sanctions;
    private IdentityScreeningService service;

    @BeforeEach
    void setUp() {
        policies = mock(IdvPolicyService.class);
        engine = mock(DeterministicAmlEngine.class);
        sanctions = mock(SanctionsMatchPort.class);
        service = new IdentityScreeningService(
                policies,
                engine,
                sanctions,
                mock(Aml2Decider.class),
                mock(KycProperties.class),
                new ObjectMapper());
    }

    @Test
    void policyWithoutScreeningDoesNotCallScreeningOrKeepAReport() {
        when(policies.applied(any())).thenReturn(applied(false));
        Verification verification = decided();
        verification.rememberScreening(
                "{\"status\":\"completed\",\"outcome\":\"potential_match\",\"subject\":{\"first_name\":\"Ada\"},\"hits\":[]}");
        Integration live = integration(verification, IntegrationMode.LIVE);

        service.apply(verification, live, Instant.parse("2026-10-31T12:05:00Z"));
        service.ensureReport(verification);

        assertNull(verification.getScreeningReport());
        verify(engine, never()).screen(any(), any());
        verify(sanctions, never()).match(any());
    }

    @Test
    void readingAVerificationNeverCallsScreening() {
        when(policies.applied(any())).thenReturn(applied(true));
        Verification verification = decided();

        service.ensureReport(verification);

        assertNull(verification.getScreeningReport());
        verify(engine, never()).screen(any(), any());
        verify(sanctions, never()).match(any());
    }

    @Test
    void decisionCallsScreeningOnlyWhenThePolicyEnablesIt() {
        when(policies.applied(any())).thenReturn(applied(true));
        when(engine.screen(any(), any()))
                .thenReturn(new DeterministicAmlEngine.EngineResult(ScreeningOutcome.CLEAR, List.of()));
        Verification verification = decided();

        service.apply(verification, integration(verification, IntegrationMode.TEST), Instant.parse("2026-10-31T12:05:00Z"));

        assertTrue(verification.getScreeningReport().contains("\"outcome\":\"clear\""));
        verify(engine).screen(any(), any());
        verify(sanctions, never()).match(any());
    }

    private static IdvPolicyService.Applied applied(boolean screeningEnabled) {
        return new IdvPolicyService.Applied(
                DecisionProfile.platform(),
                null,
                3,
                3,
                false,
                screeningEnabled,
                IdentityScreeningEffect.REVIEW);
    }

    private static Integration integration(Verification verification, IntegrationMode mode) {
        return new Integration(
                verification.getIntegrationId(),
                verification.getOrganizationId(),
                ProductCode.IDENTITY,
                mode,
                "Identity",
                Instant.parse("2026-10-31T12:00:00Z"));
    }

    private static Verification decided() {
        Instant now = Instant.parse("2026-10-31T12:00:00Z");
        UUID policyId = UUID.randomUUID();
        Verification verification = new Verification(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Ada",
                "Lovelace",
                null,
                null,
                "hash",
                now.plusSeconds(3600),
                null,
                now);
        verification.attachPolicy(policyId, 4);
        verification.decide(
                VerificationDecision.APPROVED,
                "[\"liveness_pass\"]",
                "idv-1",
                "{\"first_name\":\"Ada\",\"last_name\":\"Lovelace\",\"birth_date\":\"1990-01-01\",\"document_country\":\"FR\"}",
                now);
        return verification;
    }
}
