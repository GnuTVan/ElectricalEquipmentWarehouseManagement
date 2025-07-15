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

    boolean existsByTaxCode(String taxCode);
    SupplierDTO findByTaxCode(String taxCode);

    Page<SupplierDTO> searchSuppliers(int page, String keyword);
}