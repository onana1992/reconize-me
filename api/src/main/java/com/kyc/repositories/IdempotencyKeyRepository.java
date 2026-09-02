package com.kyc.repositories;

import com.kyc.entities.IdempotencyKey;
import com.kyc.entities.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {}
