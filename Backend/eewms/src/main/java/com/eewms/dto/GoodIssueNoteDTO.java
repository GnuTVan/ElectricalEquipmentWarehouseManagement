package com.eewms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodIssueNoteDTO {
    private Long id;
    private String code;
    private String customerName;
    private String createdBy;
    private LocalDateTime issueDate;
    private String description;
    private BigDecimal totalAmount;

    // ================== Liên kết đơn bán ==================
    private String saleOrderCode; // mã đơn bán
    private Long saleOrderId;     // NEW: id đơn bán (dùng để tạo công nợ)

    // ================== Chi tiết xuất ====================
    private List<GoodIssueDetailDTO> details;

    // ================== Công nợ / Thanh toán =============
    private String status;                 // trạng thái thanh toán
    private Long debtId;                   // id công nợ (nếu đã có)
    private BigDecimal remainingAmount;    // số tiền còn lại (total - paid)

    @Builder.Default
    private boolean hasDebt = false;       // có công nợ chưa? (default false)
}
