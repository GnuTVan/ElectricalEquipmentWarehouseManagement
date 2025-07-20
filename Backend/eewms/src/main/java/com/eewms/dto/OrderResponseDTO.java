package com.eewms.dto;

import com.eewms.entities.Order;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDTO {
    private Integer orderId;
    private String orderCode;
    private String customerName;
    private String description;
    private Order.OrderStatus status;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount; // tổng tiền đơn hàng
    private List<OrderDetailDTO> details;
}
