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

    // Tạo DTO sản phẩm từ chi tiết đơn hàng
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

    /** Map SaleOrder → SaleOrderResponseDTO */
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

        List<SaleOrderDetailDTO> detailsDTOs =
                order.getDetails() == null ? List.of()
                        : order.getDetails().stream()
                        .filter(Objects::nonNull)
                        .map(SaleOrderMapper::toDetailDTO)
                        .collect(Collectors.toList());
        if (order == null) return null;

        // An toàn với LAZY: chỉ truy cập details nếu đã được initialize
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
                .orderId(order.getSoId()) // <-- đảm bảo không null ở nơi còn dùng orderId
                .orderCode(order.getSoCode())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getFullName() : null)
                .customerName(c != null ? c.getFullName() : null)
                .customerPhone(c != null ? c.getPhone() : null) // đã thêm cột Phone trước đó
                .description(order.getDescription())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .totalAmount(total)
                .createdBy(order.getCreatedByUser() != null ? order.getCreatedByUser().getFullName() : null)
                .details(detailsDTOs)
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


