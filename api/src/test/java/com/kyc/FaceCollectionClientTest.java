package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.adapters.InMemoryFaceCollectionClient;
import com.kyc.adapters.InMemoryFaceCollectionClient.FaceCollectionException;
import com.kyc.ports.FaceCollectionClient;
import com.kyc.ports.FaceCollectionClient.FaceMatch;
import com.kyc.ports.FaceCollectionClient.IndexResult;
import com.kyc.ports.FaceCollectionClient.SearchResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FaceCollectionClientTest {

    private InMemoryFaceCollectionClient client;
    private String collectionId;
    private final byte[] selfie = "liveness-reference-bytes".getBytes(StandardCharsets.UTF_8);
    private final String enrollmentId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        client = new InMemoryFaceCollectionClient();
        collectionId = client.ensureCollection(UUID.randomUUID());
    }

    @Test
    void ensureCollectionIsIdempotentAndOpaque() {
        UUID orgId = UUID.randomUUID();
        String first = client.ensureCollection(orgId);
        String second = client.ensureCollection(orgId);
        assertEquals(first, second);
        assertTrue(first.startsWith("rm-auth-"));
        assertTrue(first.contains(orgId.toString()));
        assertFalse(first.toLowerCase().contains("acme"));
    }

    @Test
    void indexThenSearchFindsSameFaceId() {
        IndexResult indexed = client.indexFaces(collectionId, selfie, enrollmentId);
        IndexResult.Ok ok = assertInstanceOf(IndexResult.Ok.class, indexed);

        SearchResult searched = client.searchFacesByImage(collectionId, selfie, 80f);
        SearchResult.Ok found = assertInstanceOf(SearchResult.Ok.class, searched);
        assertEquals(1, found.matches().size());
        FaceMatch match = found.matches().get(0);
        assertEquals(ok.faceId(), match.faceId());
        assertTrue(match.similarity() >= 80f);
    }

    @Test
    void deleteThenSearchReturnsNoCandidates() {
        IndexResult.Ok ok =
                assertInstanceOf(IndexResult.Ok.class, client.indexFaces(collectionId, selfie, enrollmentId));
        client.deleteFaces(collectionId, List.of(ok.faceId()));
        assertFalse(client.containsFace(collectionId, ok.faceId()));

        SearchResult searched = client.searchFacesByImage(collectionId, selfie, 80f);
        SearchResult.Ok found = assertInstanceOf(SearchResult.Ok.class, searched);
        assertTrue(found.matches().isEmpty());
    }

    @Test
    void searchTimeoutIsTypedAndNeverInventedMatches() {
        assertInstanceOf(IndexResult.Ok.class, client.indexFaces(collectionId, selfie, enrollmentId));
        client.failNextSearch("provider_timeout");

        SearchResult searched = client.searchFacesByImage(collectionId, selfie, 80f);
        SearchResult.Failed failed = assertInstanceOf(SearchResult.Failed.class, searched);
        assertEquals("provider_timeout", failed.reason());
    }

    @Test
    void indexZeroFacesAndMultipleFacesAreTypedFailures() {
        client.nextIndexDetectsFaces(0);
        assertEquals(
                "zero_faces",
                assertInstanceOf(IndexResult.Failed.class, client.indexFaces(collectionId, selfie, enrollmentId))
                        .reason());

        client.nextIndexDetectsFaces(2);
        assertEquals(
                "multiple_faces",
                assertInstanceOf(IndexResult.Failed.class, client.indexFaces(collectionId, selfie, enrollmentId))
                        .reason());

        client.failNextIndex("low_quality");
        assertEquals(
                "low_quality",
                assertInstanceOf(IndexResult.Failed.class, client.indexFaces(collectionId, selfie, enrollmentId))
                        .reason());
    }

    @Test
    void differentImageDoesNotMatchIndexedFace() {
        assertInstanceOf(IndexResult.Ok.class, client.indexFaces(collectionId, selfie, enrollmentId));
        byte[] other = "other-person-selfie".getBytes(StandardCharsets.UTF_8);
        SearchResult.Ok found =
                assertInstanceOf(SearchResult.Ok.class, client.searchFacesByImage(collectionId, other, 80f));
        assertTrue(found.matches().isEmpty());
    }

    @Test
    void deleteTimeoutIsTyped() {
        IndexResult.Ok ok =
                assertInstanceOf(IndexResult.Ok.class, client.indexFaces(collectionId, selfie, enrollmentId));
        client.failNextDelete("provider_timeout");
        FaceCollectionException ex = assertThrows(
                FaceCollectionException.class, () -> client.deleteFaces(collectionId, List.of(ok.faceId())));
        assertEquals("provider_timeout", ex.reason());
        assertTrue(client.containsFace(collectionId, ok.faceId()));
    }

    @Test
    void portDoesNotDependOnCompareFacesClient() {
        FaceCollectionClient port = client;
        assertInstanceOf(IndexResult.Ok.class, port.indexFaces(collectionId, selfie, enrollmentId));
    }
}
