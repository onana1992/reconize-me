package com.kyc.ports;

/** Raised when Textract / Rekognition is unavailable (timeout, 5xx). Maps to review + provider_unavailable. */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
