package com.eewms.services;

import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.entities.PurchaseRequest;
import com.eewms.constant.PRStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IPurchaseRequestService {
    PurchaseRequest create(PurchaseRequestDTO dto);
    Page<PurchaseRequestDTO> findAll(Pageable pageable);
    Optional<PurchaseRequest> findById(Long id);
    Optional<PurchaseRequestDTO> findDtoById(Long id);
    void updateStatus(Long id, PRStatus status);
    void updateItems(Long id, List<PurchaseRequestItemDTO> items);
    void generatePurchaseOrdersFromRequest(Long prId) throws Exception;

    // ✅ Mới thêm - dùng thay cho search
    Page<PurchaseRequestDTO> filter(String creator, LocalDateTime start, LocalDateTime end, Pageable pageable);
}