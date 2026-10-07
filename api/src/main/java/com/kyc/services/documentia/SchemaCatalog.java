package com.kyc.services.documentia;

import java.util.List;

public interface SchemaCatalog {

    List<ActiveSchema> findActives(String code);
}
