package com.eewms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor
public class IssueReportFilter {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long customerId;
    private Long userId;
    private String issueCode;
    private String saleOrderCode;
}
