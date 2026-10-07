package com.kyc.dto.documentia;

import java.util.UUID;

public record DocumentParse(ParsedDocument document, UUID schemaVersionId) {}
