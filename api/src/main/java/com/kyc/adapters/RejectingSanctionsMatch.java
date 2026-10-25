package com.kyc.adapters;

import com.kyc.ports.SanctionsMatchPort;
import org.springframework.stereotype.Component;

@Component
public class RejectingSanctionsMatch implements SanctionsMatchPort {

    @Override
    public MatchResult match(Query query) {
        throw new IllegalStateException("OpenSanctions is not called before live screening is in service");
    }
}
