package com.eewms.services;

import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderItemDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
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

    /* -----------------------------------------------------------
     * GIỮ NGUYÊN: findAll, findById, searchWithFilters, generateOrderCode
     * (Lưu ý: KHÔNG cộng tồn kho trong updateStatus/updateOrder nữa)
     * ----------------------------------------------------------- */
    @Transactional(readOnly = true)
    List<PurchaseOrderDTO> findAll();

    Optional<PurchaseOrder> findById(Long id);

    @Transactional
    void updateStatus(Long id, PurchaseOrderStatus status, PurchaseOrderDTO dto) throws Exception;

    String generateOrderCode();

    Page<PurchaseOrderDTO> searchWithFilters(String keyword, PurchaseOrderStatus status,
                                             LocalDateTime from, LocalDateTime to, Pageable pageable);

    // ✅ mới
    PurchaseOrder approve(Long poId, String approverName);
    PurchaseOrder cancel(Long poId, String reason, String actorName);

    // deliveryLines: dùng PurchaseOrderItemDTO.deliveryQuantity (>0 mới tính)
    PurchaseOrder receiveDelivery(Long poId, List<PurchaseOrderItemDTO> deliveryLines,
                                  String actorName, String requestId);
    //chuyen trang thai nhanh


    //edit truoc khi duyet
    PurchaseOrder updateBeforeApprove(PurchaseOrderDTO dto);

    @Transactional(readOnly = true)
    PurchaseOrder getForEdit(Long id);

    @Transactional
    WarehouseReceiptDTO prepareReceipt(Long poId,
                                       List<PurchaseOrderItemDTO> deliveryLines,
                                       String actorName,
                                       String requestId);

    @Transactional
    WarehouseReceiptDTO prepareFastComplete(Long poId, String actorName, String requestId);
}
