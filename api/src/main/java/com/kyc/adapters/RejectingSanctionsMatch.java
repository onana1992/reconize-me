package com.kyc.adapters;

import com.kyc.ports.SanctionsMatchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kyc.opensanctions", name = "enabled", havingValue = "false", matchIfMissing = true)
public class RejectingSanctionsMatch implements SanctionsMatchPort {

    @Override
    public MatchResult match(Query query) {
        throw new IllegalStateException("OpenSanctions is not called before live screening is in service");
    }
}
