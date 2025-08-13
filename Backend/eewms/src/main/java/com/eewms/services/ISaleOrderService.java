package com.eewms.services;

import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.SaleOrder;
import jakarta.transaction.Transactional;

import java.util.List;

public interface ISaleOrderService {
    SaleOrderResponseDTO createOrder(SaleOrderRequestDTO dto, String createdByUsername);
    List<SaleOrderResponseDTO> getAllOrders();
    SaleOrderResponseDTO getById(Integer orderId);
    void updateOrderStatus(Integer orderId, SaleOrder.SaleOrderStatus newStatus);
    List<SaleOrderResponseDTO> searchByKeyword(String keyword);
    SaleOrder getOrderEntityById(Integer id); // thêm để dùng tạo phiếu
    void updateOrderItems(Integer orderId, SaleOrderRequestDTO form);
}