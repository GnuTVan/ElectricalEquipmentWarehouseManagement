package com.eewms.dto;

import com.eewms.entities.Combo;
import com.eewms.entities.Customer;
import com.eewms.entities.Product;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrderDetail;
import com.eewms.entities.User;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SaleOrderMapper {

    public static SaleOrderDetail toOrderDetail(SaleOrderDetailDTO dto, Product product) {
        if (dto == null || product == null) return null;

        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setProduct(product);
        detail.setOrderedQuantity(dto.getOrderedQuantity());
        detail.setPrice(dto.getPrice());
        return detail;
    }

    public static SaleOrderDetailDTO toDetailDTO(SaleOrderDetail d) {
        if (d == null) return null;

        Product p = d.getProduct();
        Combo combo = d.getCombo();

        return SaleOrderDetailDTO.builder()
                .productId(p != null ? p.getId() : null)
                .productCode(p != null ? p.getCode() : null)
                .productName(p != null ? p.getName() : null)
                .price(d.getPrice())
                .orderedQuantity(d.getOrderedQuantity())
                .availableQuantity(p != null ? p.getQuantity() : null)
                .fromCombo(combo != null)
                .comboId(combo != null ? combo.getId() : null)
                .comboName(combo != null ? combo.getName() : null)
                .build();
    }

    /** Map SaleOrder → SaleOrderResponseDTO */
    public static SaleOrderResponseDTO toOrderResponseDTO(SaleOrder order) {
        if (order == null) return null;

        List<SaleOrderDetailDTO> detailsDTOs =
                order.getDetails() == null ? List.of()
                        : order.getDetails().stream()
                        .filter(Objects::nonNull)
                        .map(SaleOrderMapper::toDetailDTO)
                        .collect(Collectors.toList());

        Customer c = order.getCustomer();
        User u = order.getCreatedByUser();

        return SaleOrderResponseDTO.builder()
                .soId(order.getSoId())
                .orderId(order.getSoId()) // <-- đảm bảo không null ở nơi còn dùng orderId
                .orderCode(order.getSoCode())
                .customerName(c != null ? c.getFullName() : null)
                .customerPhone(c != null ? c.getPhone() : null) // đã thêm cột Phone trước đó
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .createdBy(u != null
                        ? (u.getFullName() != null ? u.getFullName() : u.getUsername())
                        : null)
                .paymentStatus(order.getPaymentStatus())
                .paymentNote(order.getPaymentNote())
                .details(detailsDTOs)
                .build();
    }
}
