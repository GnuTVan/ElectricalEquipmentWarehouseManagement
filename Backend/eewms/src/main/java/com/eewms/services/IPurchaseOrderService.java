package com.eewms.services;

import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.entities.PurchaseOrder;
import com.eewms.constant.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IPurchaseOrderService {

    PurchaseOrder create(PurchaseOrderDTO dto) throws Exception;

    List<PurchaseOrderDTO> findAll();

    Optional<PurchaseOrder> findById(Long id);

    void updateStatus(Long id, PurchaseOrderStatus status, PurchaseOrderDTO dto) throws Exception;

    String generateOrderCode(); // Tự sinh mã P00001, P00002,...

    Page<PurchaseOrderDTO> searchWithFilters(String keyword, PurchaseOrderStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Transactional(readOnly = true)
    PurchaseOrder getForEdit(Long id);
}
