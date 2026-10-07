package com.kyc.repositories;

import com.kyc.entities.DocumentField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface DocumentFieldRepository extends JpaRepository<DocumentField, UUID> {

    List<DocumentField> findByVersionIdOrderByFieldOrderAsc(UUID versionId);

    @Modifying
    void deleteByVersionId(UUID versionId);
}
