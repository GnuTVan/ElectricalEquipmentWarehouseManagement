package com.eewms.repository.purchaseRequest;

import com.eewms.entities.PurchaseRequestItem;
import com.eewms.entities.PurchaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, Long> {
    List<PurchaseRequestItem> findByPurchaseRequest(PurchaseRequest request);
}