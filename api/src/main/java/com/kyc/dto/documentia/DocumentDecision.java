package com.kyc.dto.documentia;

import java.util.List;

/** Issue métier du cahier et décision persistable. rulesVersion de ce chemin : vision-1. */
public record DocumentDecision(
        String issue, String verificationDecision, String rulesVersion, List<String> reasons) {}
