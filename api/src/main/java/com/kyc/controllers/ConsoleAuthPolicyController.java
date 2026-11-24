package com.kyc.controllers;

import com.kyc.dto.auth.AuthPolicyResponse;
import com.kyc.dto.auth.AuthPolicyVersionList;
import com.kyc.dto.auth.PublishAuthPolicyRequest;
import com.kyc.entities.AuthenticationPolicyEntity;
import com.kyc.enums.AuthOnBorderline;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
import com.kyc.services.auth.AuthPolicy;
import com.kyc.services.auth.AuthPolicyService;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/authentication/policy")
public class ConsoleAuthPolicyController {

    private final AuthPolicyService policies;

    public ConsoleAuthPolicyController(AuthPolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    public AuthPolicyResponse get(@RequestParam("integration_id") UUID integrationId) {
        var principal = CurrentConsole.require();
        var rows = policies.versions(principal.organizationId(), integrationId);
        if (rows.isEmpty()) {
            return response(AuthPolicy.factory(), false, null);
        }
        return response(AuthPolicyService.toPolicy(rows.get(0)), true, rows.get(0));
    }

    @GetMapping("/versions")
    public AuthPolicyVersionList versions(@RequestParam("integration_id") UUID integrationId) {
        var principal = CurrentConsole.require();
        List<AuthPolicyResponse> rows = policies.versions(principal.organizationId(), integrationId).stream()
                .map(row -> response(AuthPolicyService.toPolicy(row), true, row))
                .toList();
        return new AuthPolicyVersionList(rows);
    }

    @PostMapping
    public ResponseEntity<AuthPolicyResponse> publish(@RequestBody(required = false) PublishAuthPolicyRequest body) {
        var principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.POLICY_WRITE);
        if (body == null) {
            throw ApiException.validation(
                    "Policy is required", List.of(new ErrorDetail("approval_threshold", "required")));
        }
        AuthOnBorderline onBorderline = AuthOnBorderline.DECLINE;
        if (body.onBorderline() != null && !body.onBorderline().isBlank()) {
            try {
                onBorderline = AuthOnBorderline.valueOf(body.onBorderline().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiException.validation(
                        "Invalid on_borderline", List.of(new ErrorDetail("on_borderline", "invalid")));
            }
        }
        AuthenticationPolicyEntity saved = policies.publish(
                principal.organizationId(),
                principal.userId(),
                body.integrationId(),
                body.approvalThreshold(),
                body.reviewThreshold(),
                onBorderline,
                body.maxCaptureAttempts() == null ? 3 : body.maxCaptureAttempts());
        return ResponseEntity.status(201).body(response(AuthPolicyService.toPolicy(saved), true, saved));
    }

    private AuthPolicyResponse response(AuthPolicy policy, boolean published, AuthenticationPolicyEntity row) {
        boolean active = row == null || row.isActive();
        return new AuthPolicyResponse(
                policy.version(),
                policy.approvalThreshold(),
                policy.reviewThreshold(),
                policy.onBorderline().name().toLowerCase(),
                policy.maxCaptureAttempts(),
                policy.livenessRequired(),
                published,
                active && published,
                row == null ? null : row.getPublishedAt(),
                row == null ? null : policies.publisherName(row.getPublishedBy()));
    }
}
