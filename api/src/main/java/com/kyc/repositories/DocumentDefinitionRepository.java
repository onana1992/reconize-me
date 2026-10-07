package com.kyc.repositories;

import com.kyc.entities.DocumentDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentDefinitionRepository extends JpaRepository<DocumentDefinition, UUID> {

    Optional<DocumentDefinition> findByCode(String code);
}
