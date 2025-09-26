package com.eewms.services;

import com.eewms.dto.WarehouseDTO;
import com.eewms.entities.Warehouse;
import com.eewms.repository.UserRepository;

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

    //Trả về warehouseId chính của user (ưu tiên là staff, nếu không thì supervisor)
    Long findPrimaryWarehouseIdByUser(Long userId);

    //Lấy tên supervisor (manager) của kho
    String getSupervisorName(Integer warehouseId);

    //Trả về danh sách nhân viên (UserLiteView) của kho
    List<UserRepository.UserLiteView> listStaffLite(Integer warehouseId);

    //Trả về danh sách quản lý (UserLiteView) có thể làm supervisor
    List<UserRepository.UserLiteView> findAllManagersLite();

    //Trả về danh sách nhân viên (UserLiteView) chưa được phân công vào kho nào
    List<UserRepository.UserLiteView> findUnassignedStaffLite();
}
