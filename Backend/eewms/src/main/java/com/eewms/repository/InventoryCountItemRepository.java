package com.eewms.repository;

import com.eewms.entities.InventoryCountItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryCountItemRepository extends JpaRepository<InventoryCountItem, Integer> {
}