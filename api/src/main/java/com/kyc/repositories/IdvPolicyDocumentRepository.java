package com.kyc.repositories;

import com.kyc.entities.IdvPolicyDocument;
import com.kyc.entities.IdvPolicyDocumentKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdvPolicyDocumentRepository extends JpaRepository<IdvPolicyDocument, IdvPolicyDocumentKey> {

    List<IdvPolicyDocument> findByPolicyId(UUID policyId);
}
