package com.eewms.services;

import com.eewms.dto.SupplierDTO;

import java.util.List;

public interface ISupplierService {
    List<SupplierDTO> findAll();
    void createSupplier(SupplierDTO dto);
}
