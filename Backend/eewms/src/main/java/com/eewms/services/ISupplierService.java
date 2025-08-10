package com.eewms.services;

import com.eewms.dto.SupplierDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ISupplierService {
    List<SupplierDTO> findAll();

    SupplierDTO findById(Long id);

    void create(SupplierDTO dto);

    void update(SupplierDTO dto);

    void toggleStatus(Long id);

    boolean existsByNameIgnoreCase(String name);
    boolean existsByTaxCode(String taxCode);
    boolean existsByBankAccount(String bankAccount);
    boolean existsByContactMobile(String contactMobile);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByTaxCodeAndIdNot(String taxCode, Long id);
    boolean existsByBankAccountAndIdNot(String bankAccount, Long id);
    boolean existsByContactMobileAndIdNot(String contactMobile, Long id);

    SupplierDTO findByTaxCode(String taxCode);

    Page<SupplierDTO> searchSuppliers(int page, String keyword);
}