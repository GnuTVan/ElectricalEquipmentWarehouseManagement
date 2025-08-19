package com.eewms.dto;

import com.eewms.entities.SaleOrder;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleOrderResponseDTO {
    private Integer soId;
    private Integer orderId;
    private String orderCode;

    private String customerName;
    private String customerPhone;   // <<< NEW: Số điện thoại khách hàng

    private String description;
    private SaleOrder.SaleOrderStatus status;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount; // tổng tiền đơn hàng
    private List<SaleOrderDetailDTO> details;
    private String createdBy;
    private boolean hasInsufficientStock;
    private boolean alreadyExported;

    // Thông tin thanh toán
    private String qrCodeUrl;            // URL ảnh QR từ PayOS
    private String paymentLink;          // Link thanh toán từ PayOS
    private SaleOrder.PaymentStatus paymentStatus;
    private String paymentNote;          // Nội dung CK
}
