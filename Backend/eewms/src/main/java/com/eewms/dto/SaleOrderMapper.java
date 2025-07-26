package com.eewms.dto;

import com.eewms.entities.*;

import java.util.List;
import java.util.stream.Collectors;

public class SaleOrderMapper {

    // Tạo SaleOrderDetail từ DTO + Product
    public static SaleOrderDetail toOrderDetail(SaleOrderDetailDTO dto, Product product) {
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setProduct(product);
        detail.setOrderedQuantity(dto.getOrderedQuantity());
        detail.setPrice(dto.getPrice());
        return detail;
    }

    // Tạo DTO sản phẩm từ chi tiết đơn hàng
    public static SaleOrderDetailDTO toDetailDTO(SaleOrderDetail detail) {
        Product product = detail.getProduct();
        return SaleOrderDetailDTO.builder()
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .price(detail.getPrice())
                .orderedQuantity(detail.getOrderedQuantity())
                .availableQuantity(product.getQuantity())
                .build();
    }

    // Map SaleOrder → SaleOrderResponseDTO
    public static SaleOrderResponseDTO toOrderResponseDTO(SaleOrder order) {
        List<SaleOrderDetailDTO> detailsDTOs = order.getDetails().stream()
                .map(SaleOrderMapper::toDetailDTO)
                .collect(Collectors.toList());

        return SaleOrderResponseDTO.builder()
                .orderId(order.getSoId())
                .orderCode(order.getSoCode())
                .customerName(order.getCustomer().getFullName())
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .createdBy(order.getCreatedByUser().getFullName())
                .details(detailsDTOs)
                .build();
    }
}


