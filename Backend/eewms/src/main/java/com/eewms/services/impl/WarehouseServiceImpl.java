package com.eewms.services.impl;

import com.eewms.entities.Warehouse;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements IWarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Override
    public List<Warehouse> getAll() {
        return warehouseRepository.findAll();
    }

    @Override
    public Warehouse getById(Long id) {
        return warehouseRepository.findById(id).orElseThrow();
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        if (warehouse.getId() != null) {
            Warehouse existing = getById(warehouse.getId());
            existing.setName(warehouse.getName());
            existing.setDescription(warehouse.getDescription());
            return warehouseRepository.save(existing);
        } else {
            return warehouseRepository.save(warehouse); // tạo mới
        }
    }

    @Override
    public void toggleStatus(Long id) {
        Warehouse warehouse = getById(id);
        warehouse.setStatus(!warehouse.getStatus());
        warehouseRepository.save(warehouse);
    }
}