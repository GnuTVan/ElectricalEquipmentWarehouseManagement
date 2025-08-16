package com.eewms.services;

import com.eewms.dto.WarehouseDTO;
import com.eewms.entities.Warehouse;

import java.util.List;

public interface IWarehouseService {
    List<Warehouse> getAll();
    Warehouse getById(Long id);
    Warehouse save(WarehouseDTO dto);   // đổi tham số sang DTO
    void toggleStatus(Long id);
}
