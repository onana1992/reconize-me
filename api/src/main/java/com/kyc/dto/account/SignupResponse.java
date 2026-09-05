package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record SignupResponse(@JsonProperty("user_id") UUID userId) {}
