package com.eewms.dto;

import com.eewms.entities.*;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.util.Collections;
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
    // ===== Detail item mapping (dùng ở TRANG CHI TIẾT) =====
    public static SaleOrderDetailDTO toDetailDTO(SaleOrderDetail d) {
        return SaleOrderDetailDTO.builder()
                .productId(d.getProduct().getId())
                .productCode(d.getProduct().getCode())
                .productName(d.getProduct().getName())
                .price(d.getPrice())
                .orderedQuantity(d.getOrderedQuantity())
                .availableQuantity(d.getProduct().getQuantity())
                .fromCombo(d.getCombo() != null)          // nếu có quan hệ detail -> combo
                .comboId(d.getCombo() != null ? d.getCombo().getId() : null)
                .comboName(d.getCombo() != null ? d.getCombo().getName() : null)
                .build();
    }

    // ======= LITE cho TRANG DANH SÁCH: KHÔNG đụng vào details =======
    // Tránh truy cập collection LAZY và các quan hệ LAZY khác.
    public static SaleOrderResponseDTO toOrderListDTO(SaleOrder order) {
        if (order == null) return null;

        SaleOrderResponseDTO dto = SaleOrderResponseDTO.builder()
                .orderId(order.getSoId())
                .orderCode(order.getSoCode())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                // Ưu tiên đọc total đã lưu ở cột (không cần chạm details)
                .totalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                .paymentStatus(order.getPaymentStatus())
                .paymentNote(order.getPaymentNote())
                // KHÔNG map details ở list
                .details(Collections.emptyList())
                .build();

        // Tránh kích hoạt LAZY: chỉ đọc nếu đã initialized
        if (order.getCustomer() != null && Hibernate.isInitialized(order.getCustomer())) {
            dto.setCustomerName(order.getCustomer().getFullName());
        }
        if (order.getCreatedByUser() != null && Hibernate.isInitialized(order.getCreatedByUser())) {
            dto.setCreatedBy(order.getCreatedByUser().getFullName());
        }
        return dto;
    }

    // ======= VIEW (TRANG CHI TIẾT): chỉ dùng khi đã fetch-join hoặc trong @Transactional =======
    public static SaleOrderResponseDTO toOrderResponseDTO(SaleOrder order) {
        if (order == null) return null;

        // An toàn với LAZY: chỉ truy cập details nếu đã được initialize
        List<SaleOrderDetail> details =
                (order.getDetails() != null && Hibernate.isInitialized(order.getDetails()))
                        ? order.getDetails()
                        : Collections.emptyList();

        List<SaleOrderDetailDTO> detailsDTOs = details.stream()
                .map(SaleOrderMapper::toDetailDTO)
                .collect(Collectors.toList());

        BigDecimal total =
                order.getTotalAmount() != null
                        ? order.getTotalAmount()
                        : details.stream()
                        .map(d -> d.getPrice().multiply(BigDecimal.valueOf(d.getOrderedQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SaleOrderResponseDTO.builder()
                .orderId(order.getSoId())
                .orderCode(order.getSoCode())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getFullName() : null)
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .totalAmount(total)
                .createdBy(order.getCreatedByUser() != null ? order.getCreatedByUser().getFullName() : null)
                .details(detailsDTOs)
                .paymentStatus(order.getPaymentStatus())
                .paymentNote(order.getPaymentNote())
                .payOsOrderCode(order.getPayOsOrderCode())
                .build();
    }
}


