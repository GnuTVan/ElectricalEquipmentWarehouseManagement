package com.eewms.dto;

import com.eewms.entities.*;

import java.util.List;
import java.util.stream.Collectors;

public class SaleOrderMapper {

    public static SaleOrderDetail toOrderDetail(SaleOrderItemDTO dto, Product product) {
        return SaleOrderDetail.builder()
                .product(product)
                .orderedQuantity(dto.getOrderedQuantity())
                .price(dto.getPrice()) // giá tại thời điểm đặt hàng
                .build();
    }

    public static SaleOrderDetailDTO toOrderDetailDTO(SaleOrderDetail entity) {
        return SaleOrderDetailDTO.builder()
                .productCode(entity.getProduct().getCode())
                .productName(entity.getProduct().getName())
                .price(entity.getPrice())
                .quantity(entity.getOrderedQuantity())
                .build();
    }

    public static SaleOrderResponseDTO toOrderResponseDTO(SaleOrder order) {
        List<SaleOrderDetailDTO> detailDTOs = order.getDetails().stream()
                .map(SaleOrderMapper::toOrderDetailDTO)
                .collect(Collectors.toList());

        return SaleOrderResponseDTO.builder()
                .orderId(order.getSoId())
                .orderCode(order.getSoCode())
                .customerName(order.getCustomer() != null ? order.getCustomer().getFullName() : "")
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .details(detailDTOs)
                .totalAmount(order.getTotalAmount())
                .createdBy(order.getCreatedByUser() != null ? order.getCreatedByUser().getUsername() : "") // 🆕 thêm
                .build();
    }

}
