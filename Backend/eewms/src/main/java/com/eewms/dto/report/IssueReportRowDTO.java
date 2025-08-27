package com.eewms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
public class IssueReportRowDTO {
    private Long issueId;
    private String issueCode;
    private LocalDate issueDate;
    private LocalDateTime issueDateTime;
    private Long customerId;
    private String customerName;
    private Long userId;
    private String createdByName;
    private String saleOrderCode;
    private Integer totalQuantity;
    private BigDecimal totalAmount;
    private Integer totalCombos;
}
