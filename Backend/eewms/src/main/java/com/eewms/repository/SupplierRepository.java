package com.eewms.repository;

import com.eewms.entities.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByName(String name);
    boolean existsByTaxCode(String taxCode);
    Optional<Supplier> findByTaxCode(String taxCode);
}
