package com.eewms.services;

import com.eewms.entities.Warehouse;

import java.util.List;

public interface IWarehouseService {
    List<Warehouse> getAll();
    Warehouse getById(Long id);
    Warehouse save(Warehouse warehouse);
    void toggleStatus(Long id);
}
