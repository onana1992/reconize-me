package com.kyc.services;

import com.kyc.entities.AuthenticationMedia;
import com.kyc.enums.MediaKind;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.repositories.AuthenticationMediaRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session selfies exist only while the authentication is open. A terminal session drops every
 * captured attempt. The enrolled reference, stored on the source verification, is not touched.
 */
@Service
public class AuthenticationCaptureStore {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationCaptureStore.class);

    private final AuthenticationMediaRepository media;
    private final ObjectStoragePort objectStorage;

    public AuthenticationCaptureStore(AuthenticationMediaRepository media, ObjectStoragePort objectStorage) {
        this.media = media;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public void discardSessionSelfies(UUID authenticationId) {
        for (AuthenticationMedia item : media.findByAuthenticationId(authenticationId)) {
            if (item.getKind() != MediaKind.SELFIE) {
                continue;
            }
            objectStorage.delete(item.getObjectKey());
            media.delete(item);
            log.info("Authentication selfie deleted authenticationId={} attempt={}", authenticationId, item.getAttempt());
        }
    }
}
