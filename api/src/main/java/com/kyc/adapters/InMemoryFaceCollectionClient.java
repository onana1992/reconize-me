package com.kyc.adapters;

import com.kyc.ports.FaceCollectionClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Engineering fake for auth collection tests. No sockets. Exact image fingerprint match returns
 * similarity 99. Scripts next-call failures for fail-closed paths.
 */
public final class InMemoryFaceCollectionClient implements FaceCollectionClient {

    private static final float MATCH_SIMILARITY = 99f;

    private final Map<String, Map<String, IndexedFace>> collections = new ConcurrentHashMap<>();
    private final AtomicReference<String> nextIndexFailure = new AtomicReference<>();
    private final AtomicReference<String> nextSearchFailure = new AtomicReference<>();
    private final AtomicReference<String> nextDeleteFailure = new AtomicReference<>();
    private final AtomicReference<Integer> nextIndexFaceCount = new AtomicReference<>();
    private final AtomicInteger searchCalls = new AtomicInteger();

    @Override
    public String ensureCollection(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId");
        String collectionId = "rm-auth-" + organizationId;
        collections.computeIfAbsent(collectionId, ignored -> new ConcurrentHashMap<>());
        return collectionId;
    }

    @Override
    public IndexResult indexFaces(String collectionId, byte[] imageBytes, String externalImageId) {
        requireCollection(collectionId);
        Objects.requireNonNull(imageBytes, "imageBytes");
        Objects.requireNonNull(externalImageId, "externalImageId");
        String scripted = nextIndexFailure.getAndSet(null);
        if (scripted != null) {
            return new IndexResult.Failed(scripted);
        }
        Integer faceCount = nextIndexFaceCount.getAndSet(null);
        if (faceCount != null) {
            if (faceCount <= 0) {
                return new IndexResult.Failed("zero_faces");
            }
            if (faceCount > 1) {
                return new IndexResult.Failed("multiple_faces");
            }
        }
        if (imageBytes.length == 0) {
            return new IndexResult.Failed("zero_faces");
        }
        String faceId = UUID.randomUUID().toString();
        collections
                .get(collectionId)
                .put(faceId, new IndexedFace(externalImageId, fingerprint(imageBytes)));
        return new IndexResult.Ok(faceId);
    }

    @Override
    public SearchResult searchFacesByImage(String collectionId, byte[] imageBytes, float thresholdPoints) {
        searchCalls.incrementAndGet();
        requireCollection(collectionId);
        Objects.requireNonNull(imageBytes, "imageBytes");
        String scripted = nextSearchFailure.getAndSet(null);
        if (scripted != null) {
            return new SearchResult.Failed(scripted);
        }
        if (imageBytes.length == 0) {
            return new SearchResult.Ok(List.of());
        }
        String query = fingerprint(imageBytes);
        List<FaceMatch> matches = new ArrayList<>();
        for (Map.Entry<String, IndexedFace> entry : collections.get(collectionId).entrySet()) {
            if (!entry.getValue().fingerprint().equals(query)) {
                continue;
            }
            if (MATCH_SIMILARITY >= thresholdPoints) {
                matches.add(new FaceMatch(entry.getKey(), MATCH_SIMILARITY));
            }
        }
        return new SearchResult.Ok(List.copyOf(matches));
    }

    @Override
    public void deleteFaces(String collectionId, List<String> faceIds) {
        requireCollection(collectionId);
        Objects.requireNonNull(faceIds, "faceIds");
        String scripted = nextDeleteFailure.getAndSet(null);
        if (scripted != null) {
            throw new FaceCollectionException(scripted);
        }
        Map<String, IndexedFace> faces = collections.get(collectionId);
        for (String faceId : faceIds) {
            if (faceId != null) {
                faces.remove(faceId);
            }
        }
    }

    /** Next {@link #indexFaces} returns {@link IndexResult.Failed} with this reason. */
    public void failNextIndex(String reason) {
        nextIndexFailure.set(Objects.requireNonNull(reason));
    }

    /** Next {@link #searchFacesByImage} returns {@link SearchResult.Failed} with this reason. */
    public void failNextSearch(String reason) {
        nextSearchFailure.set(Objects.requireNonNull(reason));
    }

    /** Next {@link #deleteFaces} throws {@link FaceCollectionException} with this reason. */
    public void failNextDelete(String reason) {
        nextDeleteFailure.set(Objects.requireNonNull(reason));
    }

    /**
     * Next {@link #indexFaces} simulates Rekognition detecting this many faces (0 → {@code
     * zero_faces}, &gt;1 → {@code multiple_faces}).
     */
    public void nextIndexDetectsFaces(int count) {
        nextIndexFaceCount.set(count);
    }

    public boolean containsFace(String collectionId, String faceId) {
        Map<String, IndexedFace> faces = collections.get(collectionId);
        return faces != null && faces.containsKey(faceId);
    }

    public int searchCalls() {
        return searchCalls.get();
    }

    public void resetSearchCalls() {
        searchCalls.set(0);
    }

    private void requireCollection(String collectionId) {
        Objects.requireNonNull(collectionId, "collectionId");
        if (!collections.containsKey(collectionId)) {
            throw new FaceCollectionException("provider_invalid_response");
        }
    }

    private static String fingerprint(byte[] imageBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(imageBytes));
        } catch (NoSuchAlgorithmException e) {
            return HexFormat.of().formatHex(Integer.toHexString(imageBytes.length).getBytes(StandardCharsets.UTF_8));
        }
    }

    private record IndexedFace(String externalImageId, String fingerprint) {}

    /** Typed provider failure for delete (and unknown collection). */
    public static final class FaceCollectionException extends RuntimeException {
        private final String reason;

        public FaceCollectionException(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
