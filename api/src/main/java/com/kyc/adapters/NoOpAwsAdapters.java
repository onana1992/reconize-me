package com.kyc.adapters;

import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.ports.QueuePort;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class NoOpAwsAdapters implements ObjectStoragePort, QueuePort, DocumentAiPort, BiometricAiPort {

    @Override
    public String createSignedUploadUrl(String key, Duration ttl) {
        return "http://localhost:4566/" + key;
    }

    @Override
    public void publish(String queueName, String payload) {
        // Sprint 2+ : SQS réel
    }

    @Override
    public String analyzeIdentityDocument(byte[] image) {
        return "{}";
    }

    @Override
    public double compareFaces(byte[] documentPortrait, byte[] selfie) {
        return 0.0;
    }
}
