package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.adapters.AwsClientCredentials;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class AwsClientCredentialsTest {

    @Test
    void requireBuildsStaticKeys() {
        StaticCredentialsProvider provider = AwsClientCredentials.require(" AKIAEXAMPLE ", "secret", "kyc.aws.s3");
        AwsCredentials credentials = provider.resolveCredentials();
        assertEquals("AKIAEXAMPLE", credentials.accessKeyId());
        assertEquals("secret", credentials.secretAccessKey());
    }

    @Test
    void requireRejectsBlankKeys() {
        IllegalStateException missing = assertThrows(
                IllegalStateException.class, () -> AwsClientCredentials.require("", "secret", "kyc.aws.textract"));
        assertTrue(missing.getMessage().contains("kyc.aws.textract.access-key-id"));
        assertThrows(
                IllegalStateException.class, () -> AwsClientCredentials.require("AKIA", "  ", "kyc.aws.rekognition"));
    }

    @Test
    void serviceRegionOverridesFallback() {
        assertEquals(Region.US_EAST_1, AwsClientCredentials.region("us-east-1", "ca-central-1"));
        assertEquals(Region.of("ca-central-1"), AwsClientCredentials.region("", "ca-central-1"));
    }
}
