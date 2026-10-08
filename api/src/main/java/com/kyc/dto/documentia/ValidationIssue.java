package com.kyc.dto.documentia;

public record ValidationIssue(String level, String code, String severity, String field) {}
