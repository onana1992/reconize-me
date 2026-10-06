package com.kyc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kyc.adapters.S3ObjectStorage;
import com.kyc.ports.ObjectStoragePort;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

class S3ObjectStorageTest {

    private static final String KEY = "org/1/verifications/2/document/1";

    private S3Client s3;
    private S3ObjectStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        storage = new S3ObjectStorage(s3, "recognizme-media", "kyc", "http://localhost:8080", "pepper");
    }

    @Test
    void writePutsPrivateEncryptedObjectUnderPrefix() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storage.write(KEY, new byte[] {1, 2, 3}, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest request = captor.getValue();
        assertEquals("recognizme-media", request.bucket());
        assertEquals("kyc/" + KEY, request.key());
        assertEquals("image/jpeg", request.contentType());
        assertEquals(ServerSideEncryption.AES256, request.serverSideEncryption());
    }

    @Test
    void signedUrlsStayOnApiProxy() {
        ObjectStoragePort.SignedUrl upload = storage.createSignedUploadUrl(KEY, "image/jpeg", Duration.ofMinutes(5));
        ObjectStoragePort.SignedUrl get = storage.createSignedGetUrl(KEY, Duration.ofMinutes(5));
        assertTrue(upload.url().startsWith("http://localhost:8080/v1/objects?key="));
        assertTrue(upload.url().contains("method=PUT"));
        assertTrue(get.url().startsWith("http://localhost:8080/v1/objects?key="));
        assertTrue(get.url().contains("method=GET"));
        assertFalse(upload.url().contains("amazonaws.com"));
    }

    @Test
    void existsFalseWhenMissing() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        assertFalse(storage.exists(KEY));
    }

    @Test
    void sizeUsesContentLength() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(42L).build());
        assertEquals(42L, storage.size(KEY));
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3).headObject(captor.capture());
        assertEquals("kyc/" + KEY, captor.getValue().key());
    }

    @Test
    void readReturnsBytes() {
        byte[] payload = {9, 8, 7};
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), payload));
        assertArrayEquals(payload, storage.read(KEY));
    }

    @Test
    void deleteRemovesObjectAndIsIdempotent() {
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(software.amazon.awssdk.services.s3.model.DeleteObjectResponse.builder().build());
        storage.delete(KEY);
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(captor.capture());
        assertEquals("recognizme-media", captor.getValue().bucket());
        assertEquals("kyc/" + KEY, captor.getValue().key());

        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        storage.delete(KEY);
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> storage.write("../secret", new byte[] {1}, "image/jpeg"));
        verifyNoInteractions(s3);
    }

    @Test
    void stripsLeadingSlashOnPrefix() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3ObjectStorage prefixed =
                new S3ObjectStorage(client, "bucket", "/media", "http://localhost:8080", "pepper");
        prefixed.write(KEY, new byte[] {1}, "image/jpeg");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("media/" + KEY, captor.getValue().key());
    }

    @Test
    void rejectsPrefixWithDotDot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new S3ObjectStorage(s3, "bucket", "../x", "http://localhost:8080", "pepper"));
        verifyNoInteractions(s3);
    }
}
