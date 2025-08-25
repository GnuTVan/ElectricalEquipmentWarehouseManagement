package com.eewms.services;

import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.SaleOrder;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public interface ISaleOrderService {

    // 🔹 NEW: để Controller gọi khi cần sinh mã đơn an toàn
    String generateNextCode();

    // --- các hàm sẵn có ---
    SaleOrderResponseDTO createOrder(SaleOrderRequestDTO dto, String createdByUsername);
    List<SaleOrderResponseDTO> getAllOrders();
    SaleOrderResponseDTO getById(Integer orderId);
    void updateOrderStatus(Integer orderId, SaleOrder.SaleOrderStatus newStatus);
    List<SaleOrderResponseDTO> searchByKeyword(String keyword);
    SaleOrder getOrderEntityById(Integer id);
    void updatePaymentStatus(Integer orderId, SaleOrder.PaymentStatus status);
    List<Long> getComboIdsExpanded(Integer soId);
    void updateOrderItems(Integer orderId, SaleOrderRequestDTO form);
    void deleteIfPending(Integer id);
    Page<SaleOrder> searchWithFilters(
            String keyword,
            SaleOrder.SaleOrderStatus status,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    );
}
