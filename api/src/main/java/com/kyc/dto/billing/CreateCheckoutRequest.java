package com.kyc.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateCheckoutRequest(@JsonProperty("pack_minor") Long packMinor) {}
