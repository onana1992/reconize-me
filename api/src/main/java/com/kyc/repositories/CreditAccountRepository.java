package com.kyc.repositories;

import com.kyc.entities.CreditAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreditAccount a where a.organizationId = :id")
    Optional<CreditAccount> lockById(@Param("id") UUID organizationId);
}
