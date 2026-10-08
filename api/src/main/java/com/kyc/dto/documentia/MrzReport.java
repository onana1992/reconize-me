package com.kyc.dto.documentia;

import java.util.List;

public record MrzReport(
        String format,
        String status,
        List<String> lines,
        List<CheckDigit> checkDigits,
        Double mrzScore) {

    public record CheckDigit(String field, String printed, String calculated, boolean valid) {}
}
