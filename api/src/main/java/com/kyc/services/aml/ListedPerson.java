package com.kyc.services.aml;

import com.kyc.enums.AmlCategory;
import java.util.List;

public record ListedPerson(
        String entityId,
        String listedName,
        List<String> aliases,
        String birth,
        String nationality,
        String idNumber,
        List<AmlCategory> categories,
        String dataset) {

    public List<String> variants() {
        List<String> names = new java.util.ArrayList<>();
        names.add(AmlNames.normalize(listedName));
        for (String alias : aliases) {
            names.add(AmlNames.normalize(alias));
        }
        return names;
    }
}
