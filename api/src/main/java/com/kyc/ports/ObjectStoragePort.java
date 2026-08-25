package com.kyc.ports;

import java.time.Duration;

public interface ObjectStoragePort {

    String createSignedUploadUrl(String key, Duration ttl);
}
