package com.kyc.repositories;

import com.kyc.entities.DocumentDefinitionVersion;
import com.kyc.enums.SchemaStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentDefinitionVersionRepository extends JpaRepository<DocumentDefinitionVersion, UUID> {

    List<DocumentDefinitionVersion> findByDefinitionIdAndStatus(UUID definitionId, SchemaStatus status);

    List<DocumentDefinitionVersion> findByStatus(SchemaStatus status);
}
