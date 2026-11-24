package com.kyc.ports;

import java.util.List;
import java.util.UUID;

/**
 * Rekognition Face Collection for biometric authentication. Identity {@link CompareFacesClient} is
 * not used here.
 *
 * <p>{@code externalImageId} must be the enrollment id, never the client {@code external_id}.
 */
public interface FaceCollectionClient {

    /** Idempotent. Returns an opaque collection id for the organization (not the org name). */
    String ensureCollection(UUID organizationId);

    /**
     * Indexes exactly one face. Failures: {@code zero_faces}, {@code multiple_faces}, {@code
     * low_quality}, or provider reasons ({@code provider_timeout}, {@code provider_unavailable},
     * {@code provider_invalid_response}).
     */
    IndexResult indexFaces(String collectionId, byte[] imageBytes, String externalImageId);

    /**
     * Searches by image. {@code thresholdPoints} is Rekognition's 0–100 scale (review threshold ×
     * 100). Provider failures never invent matches.
     */
    SearchResult searchFacesByImage(String collectionId, byte[] imageBytes, float thresholdPoints);

    void deleteFaces(String collectionId, List<String> faceIds);

    /** Similarity in Rekognition points [0, 100]. */
    record FaceMatch(String faceId, float similarity) {}

    sealed interface IndexResult {
        record Ok(String faceId) implements IndexResult {}

        record Failed(String reason) implements IndexResult {}
    }

    sealed interface SearchResult {
        record Ok(List<FaceMatch> matches) implements SearchResult {}

        record Failed(String reason) implements SearchResult {}
    }
}
