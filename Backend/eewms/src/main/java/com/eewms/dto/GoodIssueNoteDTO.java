package com.eewms.dto;

import com.eewms.entities.GoodIssueNote;
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
    private List<GoodIssueDetailDTO> details;
}

