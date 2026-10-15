package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.ports.FaceLivenessClient;
import com.kyc.ports.ProviderUnavailableException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.AuditImage;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequestSettings;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionResponse;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsRequest;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsResponse;

public class RekognitionFaceLivenessClient implements FaceLivenessClient {

    private final RekognitionClient rekognition;

    public RekognitionFaceLivenessClient(KycProperties properties) {
        KycProperties.Aws.Rekognition rekognitionProps = properties.aws().rekognition();
        this.rekognition = RekognitionClient.builder()
                .region(AwsClientCredentials.region(rekognitionProps.region(), properties.aws().region()))
                .credentialsProvider(AwsClientCredentials.require(
                        rekognitionProps.accessKeyId(),
                        rekognitionProps.secretAccessKey(),
                        "kyc.aws.rekognition"))
                .build();
    }

    RekognitionFaceLivenessClient(RekognitionClient rekognition) {
        this.rekognition = rekognition;
    }

    @Override
    public String createSession(String clientRequestToken) {
        try {
            CreateFaceLivenessSessionResponse response = rekognition.createFaceLivenessSession(
                    CreateFaceLivenessSessionRequest.builder()
                            .clientRequestToken(clientRequestToken)
                            // Default challenge is FaceMovementAndLightChallenge.
                            .settings(CreateFaceLivenessSessionRequestSettings.builder()
                                    .auditImagesLimit(0)
                                    .build())
                            .build());
            return response.sessionId();
        } catch (RuntimeException e) {
            throw new ProviderUnavailableException("Rekognition Face Liveness session failed", e);
        }
    }

    @Override
    public LivenessResult getResults(String sessionId) {
        try {
            GetFaceLivenessSessionResultsResponse response = rekognition.getFaceLivenessSessionResults(
                    GetFaceLivenessSessionResultsRequest.builder().sessionId(sessionId).build());
            Float confidence = response.confidence();
            double score = confidence == null ? 0.0 : confidence.doubleValue() / 100.0;
            return new LivenessResult(score, response.statusAsString(), referenceBytes(response.referenceImage()));
        } catch (RuntimeException e) {
            throw new ProviderUnavailableException("Rekognition Face Liveness results failed", e);
        }
    }

    private static byte[] referenceBytes(AuditImage image) {
        if (image == null || image.bytes() == null) {
            return new byte[0];
        }
        SdkBytes bytes = image.bytes();
        return bytes.asByteArray();
    }
}
