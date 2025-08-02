package com.eewms.services;

import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.entities.PurchaseRequest;
import com.eewms.constant.PRStatus;

import java.util.List;
import java.util.Optional;

public interface IPurchaseRequestService {
    PurchaseRequest create(PurchaseRequestDTO dto);
    List<PurchaseRequestDTO> findAll();
    Optional<PurchaseRequest> findById(Long id);
    Optional<PurchaseRequestDTO> findDtoById(Long id); // ✅ THÊM
    void updateStatus(Long id, PRStatus status);
    void updateItems(Long id, List<PurchaseRequestItemDTO> items); // ✅ THÊM
    void generatePurchaseOrdersFromRequest(Long prId) throws Exception;
}