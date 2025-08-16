package com.eewms.services.impl;

import com.eewms.dto.WarehouseDTO;
import com.eewms.entities.Warehouse;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
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
    public Warehouse save(WarehouseDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        String description = dto.getDescription() == null ? null : dto.getDescription().trim();

        if (dto.getId() == null) { // CREATE
            if (warehouseRepository.existsByNameIgnoreCase(name)) {
                throw new IllegalArgumentException("Tên kho đã tồn tại: " + name);
            }
            Warehouse w = new Warehouse();
            w.setName(name);
            w.setDescription(description);
            w.setStatus(dto.getStatus() == null ? true : dto.getStatus());
            return warehouseRepository.save(w);
        } else { // UPDATE
            if (warehouseRepository.existsByNameIgnoreCaseAndIdNot(name, dto.getId())) {
                throw new IllegalArgumentException("Tên kho đã tồn tại: " + name);
            }
            Warehouse existing = getById(dto.getId());
            existing.setName(name);
            existing.setDescription(description);
            if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
            return warehouseRepository.save(existing);
        }
    }

    @Override
    public void toggleStatus(Long id) {
        Warehouse warehouse = getById(id);
        warehouse.setStatus(!Boolean.TRUE.equals(warehouse.getStatus()));
        warehouseRepository.save(warehouse);
    }
}
