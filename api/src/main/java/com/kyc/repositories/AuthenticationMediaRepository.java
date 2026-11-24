package com.kyc.repositories;

import com.kyc.entities.AuthenticationMedia;
import com.kyc.enums.MediaKind;
import com.kyc.enums.MediaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationMediaRepository extends JpaRepository<AuthenticationMedia, UUID> {

    List<AuthenticationMedia> findByAuthenticationId(UUID authenticationId);

    Optional<AuthenticationMedia> findFirstByAuthenticationIdAndKindAndStatusOrderByAttemptDesc(
            UUID authenticationId, MediaKind kind, MediaStatus status);

    long countByAuthenticationId(UUID authenticationId);
}
