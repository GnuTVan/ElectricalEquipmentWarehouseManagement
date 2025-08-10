package com.eewms.repository;

import com.eewms.entities.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByTaxCode(String taxCode);

    Page<Supplier> findByNameContainingIgnoreCaseOrTaxCodeContainingIgnoreCase(String name, String taxCode, Pageable pageable);

    //Check duplicate CREATE
    boolean existsByNameIgnoreCase(String name);

    boolean existsByTaxCode(String taxCode);

    boolean existsByBankAccount(String bankAccount);

    boolean existsByContactMobile(String contactMobile);

    //Check duplicate UPDATE
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByTaxCodeAndIdNot(String taxCode, Long id);

    boolean existsByBankAccountAndIdNot(String bankAccount, Long id);

    boolean existsByContactMobileAndIdNot(String contactMobile, Long id);


}


