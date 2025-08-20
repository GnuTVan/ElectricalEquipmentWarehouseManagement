package com.eewms.dto;

import com.eewms.entities.Combo;
import com.eewms.entities.Customer;
import com.eewms.entities.Product;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrderDetail;
import com.eewms.entities.User;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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

    // ===== Detail item mapping (dùng ở TRANG CHI TIẾT) =====
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

    // ======= LITE cho TRANG DANH SÁCH: KHÔNG đụng vào details =======
    public static SaleOrderResponseDTO toOrderListDTO(SaleOrder order) {
        if (order == null) return null;

        Customer c = order.getCustomer();
        User u = order.getCreatedByUser();

        SaleOrderResponseDTO dto = SaleOrderResponseDTO.builder()
                .soId(order.getSoId())
                .orderId(order.getSoId())
                .orderCode(order.getSoCode())
                .customerId(c != null ? c.getId() : null)
                .customerName(c != null ? c.getFullName() : null)
                .customerPhone(c != null ? c.getPhone() : null) // <<< map số điện thoại KH
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                .paymentStatus(order.getPaymentStatus())
                .paymentNote(order.getPaymentNote())
                .createdBy(u != null
                        ? (u.getFullName() != null ? u.getFullName() : u.getUsername())
                        : null)
                .details(Collections.emptyList()) // list page không load details
                .build();

        return dto;
    }

    // ======= VIEW (TRANG CHI TIẾT): chỉ dùng khi đã fetch-join hoặc trong @Transactional =======
    public static SaleOrderResponseDTO toOrderResponseDTO(SaleOrder order) {
        if (order == null) return null;

        List<SaleOrderDetail> details =
                (order.getDetails() != null && Hibernate.isInitialized(order.getDetails()))
                        ? order.getDetails()
                        : Collections.emptyList();

        List<SaleOrderDetailDTO> detailsDTOs = details.stream()
                .map(SaleOrderMapper::toDetailDTO)
                .collect(Collectors.toList());

        Customer c = order.getCustomer();
        User u = order.getCreatedByUser();

        BigDecimal total =
                order.getTotalAmount() != null
                        ? order.getTotalAmount()
                        : details.stream()
                        .map(d -> d.getPrice().multiply(BigDecimal.valueOf(d.getOrderedQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SaleOrderResponseDTO.builder()
                .soId(order.getSoId())
                .orderId(order.getSoId())
                .orderCode(order.getSoCode())
                .customerId(c != null ? c.getId() : null)
                .customerName(c != null ? c.getFullName() : null)
                .customerPhone(c != null ? c.getPhone() : null) // map phone cho trang chi tiết
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .totalAmount(total)
                .createdBy(u != null
                        ? (u.getFullName() != null ? u.getFullName() : u.getUsername())
                        : null)
                .details(detailsDTOs)
                .paymentStatus(order.getPaymentStatus())
                .paymentNote(order.getPaymentNote())
                .build();
    }
}
