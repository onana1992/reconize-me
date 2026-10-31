package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.dto.idv.IdvPolicyResponse;
import com.kyc.dto.idv.PublishIdvPolicyRequest;
import com.kyc.entities.IdvPolicy;
import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.repositories.IdvPolicyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "kyc.redis.enabled=false")
@Transactional
class IdvPolicyServiceTest {

    @Autowired
    private IdvPolicyService policies;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private IdvPolicyRepository stored;

    @Test
    void publishVersionsAndRejectsOutOfBounds() {
        UUID organizationId = UUID.randomUUID();
        organizations.save(new Organization(organizationId, "Rules", "rules-" + organizationId.toString().substring(0, 8), Instant.now()));
        Integration live = integrations.save(new Integration(
                UUID.randomUUID(), organizationId, ProductCode.IDENTITY, IntegrationMode.LIVE, "Production", Instant.now()));
        Integration test = integrations.save(new Integration(
                UUID.randomUUID(), organizationId, ProductCode.IDENTITY, IntegrationMode.TEST, "Sandbox", Instant.now()));

        IdvPolicyResponse platform = policies.effective(organizationId, live.getId());
        assertEquals("platform", platform.source());
        assertNull(platform.version());
        assertTrue(platform.documents().stream().anyMatch(document -> "QUEBEC_DRIVER_LICENSE".equals(document.code()) && document.enabled()));

        IdvPolicyResponse first = policies.publish(organizationId, UUID.randomUUID(), sameAs(platform, live.getId()));
        assertEquals("organization", first.source());
        assertEquals(1, first.version());
        assertEquals(1, policies.creation(live).policyVersion());
        assertNull(policies.creation(test).policyVersion());

        IdvPolicyResponse second = policies.publish(organizationId, UUID.randomUUID(), sameAs(platform, live.getId()));
        assertEquals(2, second.version());
        long active = stored.findByIntegrationIdOrderByVersionDesc(live.getId()).stream().filter(IdvPolicy::isActive).count();
        assertEquals(1, active);

        ApiException low = assertThrows(ApiException.class, () -> policies.publish(organizationId, UUID.randomUUID(), withOcr(platform, live.getId(), 0.50)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, low.status());

        ApiException empty = assertThrows(
                ApiException.class, () -> policies.publish(organizationId, UUID.randomUUID(), withCodes(platform, live.getId(), List.of())));
        assertEquals("empty", empty.details().get(0).code());

        ApiException order = assertThrows(
                ApiException.class, () -> policies.publish(organizationId, UUID.randomUUID(), withFaces(platform, live.getId(), 0.96, 0.90)));
        assertEquals("order", order.details().get(0).code());

        ApiException sandbox = assertThrows(
                ApiException.class, () -> policies.publish(organizationId, UUID.randomUUID(), sameAs(platform, test.getId())));
        assertEquals("invalid", sandbox.details().get(0).code());
    }

    private static PublishIdvPolicyRequest sameAs(IdvPolicyResponse policy, UUID integrationId) {
        return new PublishIdvPolicyRequest(
                new PublishIdvPolicyRequest.Profile(
                        policy.profile().expiredOutcome(),
                        policy.profile().attemptsOutcome(),
                        policy.profile().ocrReviewAt(),
                        policy.profile().livenessPassAt(),
                        policy.profile().faceMatchFailAt(),
                        policy.profile().faceMatchPassAt()),
                policy.documents().stream().filter(IdvPolicyResponse.Document::enabled).map(IdvPolicyResponse.Document::code).toList(),
                new PublishIdvPolicyRequest.Flow(
                        policy.flow().linkTtlSeconds(),
                        policy.flow().maxCaptureAttempts(),
                        policy.flow().maxLivenessAttempts()),
                new PublishIdvPolicyRequest.Screening(policy.screening().enabled(), policy.screening().onMatch()),
                integrationId);
    }

    private static PublishIdvPolicyRequest withOcr(IdvPolicyResponse policy, UUID integrationId, double ocr) {
        PublishIdvPolicyRequest base = sameAs(policy, integrationId);
        return new PublishIdvPolicyRequest(
                new PublishIdvPolicyRequest.Profile(
                        base.profile().expiredOutcome(),
                        base.profile().attemptsOutcome(),
                        ocr,
                        base.profile().livenessPassAt(),
                        base.profile().faceMatchFailAt(),
                        base.profile().faceMatchPassAt()),
                base.documentCodes(),
                base.flow(),
                base.screening(),
                integrationId);
    }

    private static PublishIdvPolicyRequest withCodes(IdvPolicyResponse policy, UUID integrationId, List<String> codes) {
        PublishIdvPolicyRequest base = sameAs(policy, integrationId);
        return new PublishIdvPolicyRequest(base.profile(), codes, base.flow(), base.screening(), integrationId);
    }

    private static PublishIdvPolicyRequest withFaces(IdvPolicyResponse policy, UUID integrationId, double fail, double pass) {
        PublishIdvPolicyRequest base = sameAs(policy, integrationId);
        return new PublishIdvPolicyRequest(
                new PublishIdvPolicyRequest.Profile(
                        base.profile().expiredOutcome(),
                        base.profile().attemptsOutcome(),
                        base.profile().ocrReviewAt(),
                        base.profile().livenessPassAt(),
                        fail,
                        pass),
                base.documentCodes(),
                base.flow(),
                base.screening(),
                integrationId);
    }
}
