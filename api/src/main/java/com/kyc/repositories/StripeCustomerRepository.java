package com.kyc.repositories;

import com.kyc.entities.StripeCustomer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, UUID> {

    Optional<StripeCustomer> findByStripeCustomerId(String stripeCustomerId);
}
