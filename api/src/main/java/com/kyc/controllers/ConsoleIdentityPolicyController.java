package com.kyc.controllers;

import com.kyc.dto.idv.IdvPolicyResponse;
import com.kyc.dto.idv.IdvPolicyVersionList;
import com.kyc.dto.idv.PublishIdvPolicyRequest;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.CurrentConsole;
import com.kyc.security.Permission;
import com.kyc.services.IdvPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/identity/policy")
@Tag(name = "Console")
public class ConsoleIdentityPolicyController {

    private final IdvPolicyService policies;

    public ConsoleIdentityPolicyController(IdvPolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    @Operation(summary = "Politique IDV effective d'une intégration de production")
    public IdvPolicyResponse get(@RequestParam("integration_id") UUID integrationId) {
        ConsolePrincipal principal = CurrentConsole.require();
        return policies.effective(principal.organizationId(), integrationId);
    }

    @GetMapping("/versions")
    @Operation(summary = "Versions publiées de la politique IDV d'une intégration de production")
    public IdvPolicyVersionList versions(@RequestParam("integration_id") UUID integrationId) {
        ConsolePrincipal principal = CurrentConsole.require();
        return policies.versions(principal.organizationId(), integrationId);
    }

    @PostMapping
    @Operation(summary = "Publier une version de la politique IDV")
    public ResponseEntity<IdvPolicyResponse> publish(@RequestBody(required = false) PublishIdvPolicyRequest body) {
        ConsolePrincipal principal = CurrentConsole.require();
        ConsoleAuth.require(principal, Permission.POLICY_WRITE);
        return ResponseEntity.status(201).body(policies.publish(principal.organizationId(), principal.userId(), body));
    }
}
