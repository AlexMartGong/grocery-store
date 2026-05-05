package com.amx.grocerystore.customers.infrastructure;

import com.amx.grocerystore.customers.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndStoreId(UUID id, UUID storeId);

    @Query("SELECT c FROM Customer c WHERE c.storeId = :storeId " +
            "AND (:active IS NULL OR c.active = :active) " +
            "AND (:search = '' OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Customer> findCustomers(@Param("storeId") UUID storeId,
                                 @Param("active") Boolean active,
                                 @Param("search") String search,
                                 Pageable pageable);
}
