package com.kyc.repositories;

import com.kyc.entities.DocumentValidationRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface DocumentValidationRuleRepository extends JpaRepository<DocumentValidationRule, UUID> {

    List<DocumentValidationRule> findByVersionId(UUID versionId);

    @Modifying
    void deleteByVersionId(UUID versionId);
}
