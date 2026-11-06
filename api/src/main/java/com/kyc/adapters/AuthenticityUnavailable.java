package com.kyc.adapters;

/** Le service anti-spoof n'a pas rendu un assessment. */
public class AuthenticityUnavailable extends RuntimeException {

    public AuthenticityUnavailable(String kind) {
        super(kind);
    }
}
