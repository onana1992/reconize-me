package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.ports.CompareFacesClient;
import com.kyc.ports.ProviderUnavailableException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Image;

public class RekognitionCompareFacesClient implements CompareFacesClient {

    private final RekognitionClient rekognition;

    public RekognitionCompareFacesClient(KycProperties properties) {
        this.rekognition = RekognitionClient.builder()
                .region(Region.of(properties.aws().region()))
                .build();
    }

    RekognitionCompareFacesClient(RekognitionClient rekognition) {
        this.rekognition = rekognition;
    }

    @Override
    public double compare(byte[] documentImage, byte[] selfieImage) {
        try {
            CompareFacesResponse response = rekognition.compareFaces(CompareFacesRequest.builder()
                    .sourceImage(Image.builder().bytes(SdkBytes.fromByteArray(selfieImage)).build())
                    .targetImage(Image.builder().bytes(SdkBytes.fromByteArray(documentImage)).build())
                    .similarityThreshold(0f)
                    .build());
            if (response.faceMatches() == null || response.faceMatches().isEmpty()) {
                return 0.0;
            }
            Float similarity = response.faceMatches().get(0).similarity();
            return similarity == null ? 0.0 : similarity.doubleValue() / 100.0;
        } catch (RuntimeException e) {
            throw new ProviderUnavailableException("Rekognition CompareFaces call failed", e);
        }
    }
}
