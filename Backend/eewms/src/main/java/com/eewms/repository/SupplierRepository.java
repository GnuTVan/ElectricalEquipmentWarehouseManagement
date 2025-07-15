package com.eewms.repository;

import com.eewms.entities.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByTaxCode(String taxCode);

    Optional<Supplier> findByTaxCode(String taxCode);

    Page<Supplier> findByNameContainingIgnoreCaseOrTaxCodeContainingIgnoreCase(String name, String taxCode, Pageable pageable);



}


