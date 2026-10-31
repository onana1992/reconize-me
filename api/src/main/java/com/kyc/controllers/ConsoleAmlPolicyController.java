package com.kyc.controllers;

import com.kyc.dto.aml.AmlPolicyResponse;
import com.kyc.dto.aml.AmlPolicyVersionList;
import com.kyc.dto.aml.PublishAmlPolicyRequest;
import com.kyc.entities.AmlPolicyEntity;
import com.kyc.enums.AmlOnMatch;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
import com.kyc.services.aml.AmlPolicy;
import com.kyc.services.aml.AmlPolicyService;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/aml/policy")
public class ConsoleAmlPolicyController {

    private final AmlPolicyService policies;

    public ConsoleAmlPolicyController(AmlPolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    public AmlPolicyResponse get(@RequestParam("integration_id") java.util.UUID integrationId) {
        var principal = CurrentConsole.require();
        var rows = policies.versions(principal.organizationId(), integrationId);
        if (rows.isEmpty()) {
            return response(AmlPolicy.factory(), false, null);
        }
        return response(AmlPolicyService.toPolicy(rows.get(0)), true, rows.get(0));
    }

    @GetMapping("/versions")
    public AmlPolicyVersionList versions(@RequestParam("integration_id") java.util.UUID integrationId) {
        var principal = CurrentConsole.require();
        List<AmlPolicyResponse> rows = policies.versions(principal.organizationId(), integrationId).stream()
                .map(row -> response(AmlPolicyService.toPolicy(row), true, row))
                .toList();
        return new AmlPolicyVersionList(rows);
    }

    @PostMapping
    public ResponseEntity<AmlPolicyResponse> publish(@RequestBody(required = false) PublishAmlPolicyRequest body) {
        var principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.POLICY_WRITE);
        if (body == null) {
            throw ApiException.validation("Policy is required", List.of(new ErrorDetail("review_threshold", "required")));
        }
        AmlOnMatch onMatch = body.onMatch() == null || body.onMatch().isBlank()
                ? AmlOnMatch.REVIEW
                : AmlOnMatch.parse(body.onMatch());
        if (body.onMatch() != null && !body.onMatch().isBlank() && onMatch == null) {
            throw ApiException.validation("Invalid on_match", List.of(new ErrorDetail("on_match", "invalid")));
        }
        AmlPolicyEntity saved = policies.publish(
                principal.organizationId(),
                principal.userId(),
                new AmlPolicyService.PublishCommand(
                        body.integrationId(),
                        body.reviewThreshold(),
                        body.confirmThreshold(),
                        body.categories(),
                        body.attachToIdentity(),
                        onMatch,
                        body.testIntegrationId(),
                        body.liveIntegrationId()));
        return ResponseEntity.status(201).body(response(AmlPolicyService.toPolicy(saved), true, saved));
    }

    private AmlPolicyResponse response(AmlPolicy policy, boolean published, AmlPolicyEntity row) {
        return new AmlPolicyResponse(
                policy.version(),
                policy.reviewThreshold(),
                policy.confirmThreshold(),
                policy.categories().stream().map(category -> category.json()).toList(),
                policy.attachToIdentity(),
                policy.onMatch().json(),
                policy.testIntegrationId(),
                policy.liveIntegrationId(),
                published,
                row != null && row.isActive(),
                row == null ? null : row.getPublishedAt(),
                row == null ? null : policies.publisherName(row.getPublishedBy()));
    }
}
