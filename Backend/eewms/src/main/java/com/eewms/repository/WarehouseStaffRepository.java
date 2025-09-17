package com.eewms.repository;

import com.eewms.entities.WarehouseStaff;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseStaffRepository extends JpaRepository<WarehouseStaff, Integer> {

    boolean existsByWarehouse_IdAndUser_Id(Integer warehouseId, Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<WarehouseStaff> findByWarehouse_Id(Integer warehouseId);

    long countByWarehouse_Id(Integer warehouseId);

    void deleteByWarehouse_IdAndUser_Id(Integer warehouseId, Long userId);

    @EntityGraph(attributePaths = {"warehouse"})
    List<WarehouseStaff> findByUser_Id(Long userId);
}
