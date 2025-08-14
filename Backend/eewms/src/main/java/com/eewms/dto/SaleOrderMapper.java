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
    public static SaleOrderDetailDTO toDetailDTO(SaleOrderDetail d) {
        return SaleOrderDetailDTO.builder()
                .productId(d.getProduct().getId())
                .productCode(d.getProduct().getCode())
                .productName(d.getProduct().getName())
                .price(d.getPrice())                  // QUAN TRỌNG: map đúng
                .orderedQuantity(d.getOrderedQuantity())  // QUAN TRỌNG: map đúng
                .availableQuantity(d.getProduct().getQuantity())
                .fromCombo(d.getCombo() != null)          // nếu có quan hệ detail -> combo
                .comboId(d.getCombo() != null ? d.getCombo().getId() : null)
                .comboName(d.getCombo() != null ? d.getCombo().getName() : null)
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


