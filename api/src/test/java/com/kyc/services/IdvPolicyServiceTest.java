package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.dto.idv.IdvPolicyResponse;
import com.kyc.dto.idv.PublishIdvPolicyRequest;
import com.kyc.entities.IdvPolicy;
import com.kyc.entities.Organization;
import com.kyc.repositories.IdvPolicyRepository;
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
    private IdvPolicyRepository stored;

    @Test
    void publishVersionsAndRejectsOutOfBounds() {
        UUID organizationId = UUID.randomUUID();
        organizations.save(new Organization(organizationId, "Rules", "rules-" + organizationId.toString().substring(0, 8), Instant.now()));

        IdvPolicyResponse platform = policies.effective(organizationId);
        assertEquals("platform", platform.source());
        assertNull(platform.version());
        assertTrue(platform.documents().stream().anyMatch(document -> "QUEBEC_DRIVER_LICENSE".equals(document.code()) && document.enabled()));

        IdvPolicyResponse first = policies.publish(organizationId, UUID.randomUUID(), sameAs(platform));
        assertEquals("organization", first.source());
        assertEquals(1, first.version());
        assertEquals(1, policies.creation(organizationId).policyVersion());

        IdvPolicyResponse second = policies.publish(organizationId, UUID.randomUUID(), sameAs(platform));
        assertEquals(2, second.version());
        long active = stored.findByOrganizationIdOrderByVersionDesc(organizationId).stream().filter(IdvPolicy::isActive).count();
        assertEquals(1, active);

        ApiException low = assertThrows(ApiException.class, () -> policies.publish(organizationId, UUID.randomUUID(), withOcr(platform, 0.50)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, low.status());

        ApiException empty = assertThrows(
                ApiException.class, () -> policies.publish(organizationId, UUID.randomUUID(), withCodes(platform, List.of())));
        assertEquals("empty", empty.details().get(0).code());

        ApiException order = assertThrows(
                ApiException.class, () -> policies.publish(organizationId, UUID.randomUUID(), withFaces(platform, 0.96, 0.90)));
        assertEquals("order", order.details().get(0).code());
    }

    private static PublishIdvPolicyRequest sameAs(IdvPolicyResponse policy) {
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
                        policy.flow().maxLivenessAttempts()));
    }

    private static PublishIdvPolicyRequest withOcr(IdvPolicyResponse policy, double ocr) {
        PublishIdvPolicyRequest base = sameAs(policy);
        return new PublishIdvPolicyRequest(
                new PublishIdvPolicyRequest.Profile(
                        base.profile().expiredOutcome(),
                        base.profile().attemptsOutcome(),
                        ocr,
                        base.profile().livenessPassAt(),
                        base.profile().faceMatchFailAt(),
                        base.profile().faceMatchPassAt()),
                base.documentCodes(),
                base.flow());
    }

    private static PublishIdvPolicyRequest withCodes(IdvPolicyResponse policy, List<String> codes) {
        PublishIdvPolicyRequest base = sameAs(policy);
        return new PublishIdvPolicyRequest(base.profile(), codes, base.flow());
    }

    private static PublishIdvPolicyRequest withFaces(IdvPolicyResponse policy, double fail, double pass) {
        PublishIdvPolicyRequest base = sameAs(policy);
        return new PublishIdvPolicyRequest(
                new PublishIdvPolicyRequest.Profile(
                        base.profile().expiredOutcome(),
                        base.profile().attemptsOutcome(),
                        base.profile().ocrReviewAt(),
                        base.profile().livenessPassAt(),
                        fail,
                        pass),
                base.documentCodes(),
                base.flow());
    }
}
