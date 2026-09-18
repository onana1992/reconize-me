package com.kyc.repositories;

import com.kyc.entities.CreditLedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntry, UUID> {

    boolean existsByStripeEventId(String stripeEventId);

    boolean existsByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    Optional<CreditLedgerEntry> findByStripeEventId(String stripeEventId);

    Optional<CreditLedgerEntry> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    boolean existsByResourceTypeAndResourceId(String resourceType, UUID resourceId);

    @Query(
            """
            select coalesce(sum(-e.amountMinor), 0)
            from CreditLedgerEntry e
            where e.organizationId = :org
              and e.entryType = 'debit'
              and e.product = :product
            """)
    long sumDebits(@Param("org") UUID organizationId, @Param("product") String product);

    List<CreditLedgerEntry> findByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId, Pageable pageable);

    @Query(
            """
            select e from CreditLedgerEntry e
            where e.organizationId = :org
              and (e.createdAt < :created or (e.createdAt = :created and e.id < :id))
            order by e.createdAt desc, e.id desc
            """)
    List<CreditLedgerEntry> pageAfter(
            @Param("org") UUID organizationId,
            @Param("created") Instant created,
            @Param("id") UUID id,
            Pageable pageable);
}
