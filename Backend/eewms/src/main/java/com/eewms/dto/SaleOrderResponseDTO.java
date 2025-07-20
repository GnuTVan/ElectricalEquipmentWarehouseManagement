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
    private Integer orderId;
    private String orderCode;
    private String customerName;
    private String description;
    private SaleOrder.SaleOrderStatus status;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount; // tổng tiền đơn hàng
    private List<SaleOrderDetailDTO> details;
}
