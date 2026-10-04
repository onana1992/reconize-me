package com.kyc.adapters;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public final class AwsClientCredentials {

    private AwsClientCredentials() {}

    public static StaticCredentialsProvider require(String accessKeyId, String secretAccessKey, String propertyPrefix) {
        if (isBlank(accessKeyId) || isBlank(secretAccessKey)) {
            throw new IllegalStateException(
                    propertyPrefix + ".access-key-id and " + propertyPrefix + ".secret-access-key are required");
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim()));
    }

    public static Region region(String serviceRegion, String fallback) {
        String value = isBlank(serviceRegion) ? fallback : serviceRegion.trim();
        if (isBlank(value)) {
            throw new IllegalStateException("kyc.aws.region is required");
        }
        return Region.of(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
