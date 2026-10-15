package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.ports.LivenessCredentialPort;
import com.kyc.ports.ProviderUnavailableException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * AssumeRole minimum is 900 seconds. The Face Liveness session id itself expires in 3 minutes.
 * The session policy allows only StartFaceLivenessSession.
 */
public class StsLivenessCredentials implements LivenessCredentialPort {

    static final String SESSION_POLICY = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"rekognition:StartFaceLivenessSession","Resource":"*"}]}""";

    private final StsClient sts;
    private final String roleArn;

    public StsLivenessCredentials(KycProperties properties, String roleArn) {
        KycProperties.Aws.Rekognition rekognitionProps = properties.aws().rekognition();
        this.roleArn = roleArn == null ? "" : roleArn.trim();
        this.sts = StsClient.builder()
                .region(AwsClientCredentials.region(rekognitionProps.region(), properties.aws().region()))
                .credentialsProvider(AwsClientCredentials.require(
                        rekognitionProps.accessKeyId(),
                        rekognitionProps.secretAccessKey(),
                        "kyc.aws.rekognition"))
                .build();
    }

    @Override
    public Issued issue() {
        if (roleArn.isBlank()) {
            throw new ProviderUnavailableException("kyc.aws.rekognition.liveness-role-arn is required");
        }
        try {
            AssumeRoleResponse response = sts.assumeRole(AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("kyc-liveness")
                    .durationSeconds(900)
                    .policy(SESSION_POLICY)
                    .build());
            Credentials credentials = response.credentials();
            return new Issued(
                    credentials.accessKeyId(),
                    credentials.secretAccessKey(),
                    credentials.sessionToken(),
                    credentials.expiration());
        } catch (RuntimeException e) {
            throw new ProviderUnavailableException("Face Liveness credentials unavailable", e);
        }
    }
}
