package com.eewms.services;

import com.eewms.dto.WarehouseDTO;
import com.eewms.entities.Warehouse;

import java.util.List;

public interface IWarehouseService {
    List<Warehouse> getAll();
    Warehouse getById(Integer id);
    Warehouse save(WarehouseDTO dto);   // đổi tham số sang DTO
    void toggleStatus(Integer id);

    //Supervisor
    void assignSupervisor(Integer warehouseId, Long userId);
    void clearSupervisor(Integer warehouseId);
    Long getSupervisorId(Integer warehouseId);

    //Staff membership
    List<Integer> listStaffIds(Integer warehouseId);
    void addStaff(Integer warehouseId, Long userId);
    void removeStaff(Integer warehouseId, Long userId);
}
