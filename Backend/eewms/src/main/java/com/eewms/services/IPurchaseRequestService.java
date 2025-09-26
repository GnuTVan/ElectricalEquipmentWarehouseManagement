package com.eewms.services;

import com.eewms.constant.PRStatus;
import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.entities.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IPurchaseRequestService {
    PurchaseRequest create(PurchaseRequestDTO dto);
    Optional<PurchaseRequestDTO> findDtoById(Long id);
    void updateStatus(Long id, PRStatus status);
    void approve(Long id);
    void updateItems(Long id, List<PurchaseRequestItemDTO> items);
    void generatePurchaseOrdersFromRequest(Long prId) throws Exception;
    Page<PurchaseRequestDTO> filter(String creator, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    void cancel(Long id, String reason);
    List<PurchaseRequestItemDTO> collectShortagesForAllOpen();
    PurchaseRequestDTO createFromCollected(List<PurchaseRequestItemDTO> items, String createdBy);
}
