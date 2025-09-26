package com.eewms.repository;

import com.eewms.entities.InventoryCount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, Integer> {
    @EntityGraph(attributePaths = {"warehouse", "assignedStaff", "items", "items.product"})
    List<InventoryCount> findAll();

    @EntityGraph(attributePaths = {"warehouse", "assignedStaff", "items", "items.product"})
    List<InventoryCount> findByAssignedStaff_Id(Long staffId);

    @Query("SELECT ic FROM InventoryCount ic " +
            "JOIN FETCH ic.warehouse " +
            "LEFT JOIN FETCH ic.assignedStaff " +
            "LEFT JOIN FETCH ic.items it " +
            "LEFT JOIN FETCH it.product " +
            "WHERE ic.id = :id")
    Optional<InventoryCount> findWithDetailsById(@Param("id") Integer id);
}