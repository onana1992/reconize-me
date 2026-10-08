package com.kyc.services.documentia;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class QuebecSchemaSeed implements ApplicationRunner {

    private final SchemaRegistry registry;

    public QuebecSchemaSeed(SchemaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        registry.ensureQuebecLicense();
        registry.ensureQuebecLicenseBack();
        registry.ensurePassportTd3();
        registry.ensureCanadaPermanentResident();
        registry.ensureCanadaPermanentResidentBack();
    }
}
