package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record MeResponse(
        String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        OrganizationMe organization,
        String role) {

    public record OrganizationMe(UUID id, String name, String slug, String plan) {}
}
