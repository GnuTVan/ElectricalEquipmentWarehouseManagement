package com.eewms.services;

import com.eewms.dto.OrderRequestDTO;
import com.eewms.dto.OrderResponseDTO;
import com.eewms.entities.Order;
import jakarta.transaction.Transactional;

import java.util.List;

public interface IOrderService {
    OrderResponseDTO createOrder(OrderRequestDTO dto, String createdByUsername);
    List<OrderResponseDTO> getAllOrders();
    OrderResponseDTO getById(Integer orderId);
    void cancelOrder(Integer orderId);
    boolean canApprove(Integer orderId);
    @Transactional
    void updateOrderStatus(Integer orderId, Order.OrderStatus newStatus, String username);
}
