package com.kyc.dto.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertWebhookRequest(@NotBlank @Size(max = 2048) String url) {}
