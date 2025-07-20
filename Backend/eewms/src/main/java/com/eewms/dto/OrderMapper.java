package com.eewms.dto;

import com.eewms.dto.*;
import com.eewms.entities.*;

import java.util.List;
import java.util.stream.Collectors;

public class OrderMapper {

    public static OrderDetail toOrderDetail(OrderItemDTO dto, Product product) {
        return OrderDetail.builder()
                .product(product)
                .orderedQuantity(dto.getOrderedQuantity())
                .price(dto.getPrice()) // giá tại thời điểm đặt hàng
                .build();
    }

    public static OrderDetailDTO toOrderDetailDTO(OrderDetail entity) {
        return OrderDetailDTO.builder()
                .productCode(entity.getProduct().getCode())
                .productName(entity.getProduct().getName())
                .price(entity.getPrice())
                .quantity(entity.getOrderedQuantity())
                .build();
    }

    public static OrderResponseDTO toOrderResponseDTO(Order order) {
        List<OrderDetailDTO> detailDTOs = order.getDetails().stream()
                .map(OrderMapper::toOrderDetailDTO)
                .collect(Collectors.toList());

        return OrderResponseDTO.builder()
                .orderId(order.getPoId())
                .orderCode(order.getPoCode())
                .customerName(order.getCustomer() != null ? order.getCustomer().getFullName() : "")
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .details(detailDTOs)
                .totalAmount(order.getTotalAmount()) // lấy từ entity
                .build();
    }
}
