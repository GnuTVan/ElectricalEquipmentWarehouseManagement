package com.eewms.repository;

import com.eewms.entities.PurchaseOrder;
import com.eewms.constant.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByCode(String code);

    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);
}
