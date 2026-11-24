package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.ports.FaceCollectionClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteFacesRequest;
import software.amazon.awssdk.services.rekognition.model.FaceRecord;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.IndexFacesRequest;
import software.amazon.awssdk.services.rekognition.model.IndexFacesResponse;
import software.amazon.awssdk.services.rekognition.model.QualityFilter;
import software.amazon.awssdk.services.rekognition.model.Reason;
import software.amazon.awssdk.services.rekognition.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageRequest;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse;
import software.amazon.awssdk.services.rekognition.model.UnindexedFace;

/**
 * Production collection in {@code us-east-1}. Logs never include image bytes, FaceId, or the client
 * external id. {@code externalImageId} is the enrollment id.
 */
public class RekognitionFaceCollectionClient implements FaceCollectionClient {

    private static final Logger log = LoggerFactory.getLogger(RekognitionFaceCollectionClient.class);

    private final RekognitionClient rekognition;

    public RekognitionFaceCollectionClient(KycProperties properties) {
        KycProperties.Aws.Rekognition rekognitionProps = properties.aws().rekognition();
        this.rekognition = RekognitionClient.builder()
                .region(AwsClientCredentials.region(rekognitionProps.region(), properties.aws().region()))
                .credentialsProvider(AwsClientCredentials.require(
                        rekognitionProps.accessKeyId(),
                        rekognitionProps.secretAccessKey(),
                        "kyc.aws.rekognition"))
                .build();
    }

    RekognitionFaceCollectionClient(RekognitionClient rekognition) {
        this.rekognition = rekognition;
    }

    @Override
    public String ensureCollection(UUID organizationId) {
        String collectionId = collectionId(organizationId);
        try {
            rekognition.createCollection(CreateCollectionRequest.builder().collectionId(collectionId).build());
        } catch (ResourceAlreadyExistsException ignored) {
            return collectionId;
        } catch (RuntimeException e) {
            log.info("face_collection.ensure_failed reason={}", reasonOf(e));
            throw new IllegalStateException(reasonOf(e), e);
        }
        return collectionId;
    }

    @Override
    public IndexResult indexFaces(String collectionId, byte[] imageBytes, String externalImageId) {
        try {
            IndexFacesResponse response = rekognition.indexFaces(IndexFacesRequest.builder()
                    .collectionId(collectionId)
                    .externalImageId(externalImageId)
                    .maxFaces(2)
                    .qualityFilter(QualityFilter.AUTO)
                    .image(image(imageBytes))
                    .build());
            List<FaceRecord> records = response.faceRecords() == null ? List.of() : response.faceRecords();
            List<UnindexedFace> unindexed = response.unindexedFaces() == null ? List.of() : response.unindexedFaces();
            if (records.size() != 1 || !unindexed.isEmpty()) {
                deleteQuietly(collectionId, records);
                if (records.isEmpty() && unindexed.stream().anyMatch(RekognitionFaceCollectionClient::lowQuality)) {
                    return new IndexResult.Failed("low_quality");
                }
                if (records.isEmpty()) {
                    return new IndexResult.Failed("zero_faces");
                }
                return new IndexResult.Failed("multiple_faces");
            }
            String faceId = records.get(0).face() == null ? null : records.get(0).face().faceId();
            if (faceId == null || faceId.isBlank()) {
                return new IndexResult.Failed("provider_invalid_response");
            }
            return new IndexResult.Ok(faceId);
        } catch (RuntimeException e) {
            log.info("face_collection.index_failed reason={}", reasonOf(e));
            return new IndexResult.Failed(reasonOf(e));
        }
    }

    @Override
    public SearchResult searchFacesByImage(String collectionId, byte[] imageBytes, float thresholdPoints) {
        try {
            SearchFacesByImageResponse response = rekognition.searchFacesByImage(SearchFacesByImageRequest.builder()
                    .collectionId(collectionId)
                    .faceMatchThreshold(thresholdPoints)
                    .maxFaces(10)
                    .image(image(imageBytes))
                    .build());
            List<FaceMatch> matches = new ArrayList<>();
            if (response.faceMatches() != null) {
                response.faceMatches().forEach(match -> {
                    if (match.face() != null && match.face().faceId() != null && match.similarity() != null) {
                        matches.add(new FaceMatch(match.face().faceId(), match.similarity()));
                    }
                });
            }
            return new SearchResult.Ok(matches);
        } catch (RuntimeException e) {
            String reason = reasonOf(e);
            log.info("face_collection.search_failed reason={}", reason);
            if ("no_face".equals(reason)) {
                return new SearchResult.Ok(List.of());
            }
            return new SearchResult.Failed(reason);
        }
    }

    @Override
    public void deleteFaces(String collectionId, List<String> faceIds) {
        if (faceIds == null || faceIds.isEmpty()) {
            return;
        }
        try {
            rekognition.deleteFaces(DeleteFacesRequest.builder()
                    .collectionId(collectionId)
                    .faceIds(faceIds)
                    .build());
        } catch (RuntimeException e) {
            log.info("face_collection.delete_failed reason={}", reasonOf(e));
            throw new IllegalStateException(reasonOf(e), e);
        }
    }

    static String collectionId(UUID organizationId) {
        return "rmauth" + organizationId.toString().replace("-", "");
    }

    private void deleteQuietly(String collectionId, List<FaceRecord> records) {
        List<String> ids = records.stream()
                .filter(record -> record.face() != null && record.face().faceId() != null)
                .map(record -> record.face().faceId())
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        try {
            deleteFaces(collectionId, ids);
        } catch (RuntimeException ignored) {
            log.info("face_collection.cleanup_failed");
        }
    }

    private static Image image(byte[] imageBytes) {
        return Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build();
    }

    private static boolean lowQuality(UnindexedFace face) {
        if (face.reasons() == null) {
            return false;
        }
        for (Reason reason : face.reasons()) {
            if (reason == Reason.LOW_SHARPNESS
                    || reason == Reason.LOW_BRIGHTNESS
                    || reason == Reason.LOW_CONFIDENCE
                    || reason == Reason.EXTREME_POSE) {
                return true;
            }
        }
        return false;
    }

    private static String reasonOf(RuntimeException error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ApiCallTimeoutException) {
                return "provider_timeout";
            }
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("timed out") || message.contains("timeout")) {
                return "provider_timeout";
            }
            if (message.contains("no face") || message.contains("there are no faces")) {
                return "no_face";
            }
            current = current.getCause();
        }
        return "provider_unavailable";
    }
}
