package com.eewms.repository;

import com.eewms.entities.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Integer> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    @EntityGraph(attributePaths = "supervisor")
    Optional<Warehouse> findWithSupervisorById(Long id);

    @EntityGraph(attributePaths = "supervisor")
    Page<Warehouse> findAllBy(Pageable pageable);

    List<Warehouse> findBySupervisor_Id(Long supervisorId);

    @Query("""
            SELECT DISTINCT w FROM Warehouse w
            LEFT JOIN WarehouseStaff ws ON ws.warehouse.id = w.id
            WHERE w.supervisor.id = :userId OR ws.user.id = :userId
            """)
    List<Warehouse> findAccessibleByUserId(@Param("userId") Long userId);

    /**
     * Lấy tên supervisor (manager) của kho.
     * Giả định: bảng warehouses có cột supervisor_id -> users.id
     * bảng users có cột full_name
     */
    @Query("""
                select w.supervisor.fullName
                from Warehouse w
                where w.id = :warehouseId
            """)
    String findSupervisorNameById(Integer warehouseId);
}
