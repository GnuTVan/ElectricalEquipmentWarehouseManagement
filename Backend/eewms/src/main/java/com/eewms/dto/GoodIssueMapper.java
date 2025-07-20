package com.eewms.dto;

import com.eewms.dto.*;
import com.eewms.entities.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class GoodIssueMapper {

    public static GoodIssueDetailDTO toDetailDTO(GoodIssueDetail detail) {
        BigDecimal total = detail.getPrice().multiply(BigDecimal.valueOf(detail.getQuantity()));
        return GoodIssueDetailDTO.builder()
                .productName(detail.getProduct().getName())
                .quantity(detail.getQuantity())
                .price(detail.getPrice())
                .total(total)
                .build();
    }

    public static GoodIssueNoteDTO toNoteDTO(GoodIssueNote gin) {
        List<GoodIssueDetailDTO> detailDTOs = gin.getDetails().stream()
                .map(GoodIssueMapper::toDetailDTO)
                .collect(Collectors.toList());

        return GoodIssueNoteDTO.builder()
                .id(gin.getGinId())
                .code(gin.getGinCode())
                .customerName(gin.getCustomer() != null ? gin.getCustomer().getFullName() : "")
                .createdBy(gin.getCreatedBy().getUsername())
                .issueDate(gin.getIssueDate())
                .status(gin.getStatus())
                .description(gin.getDescription())
                .details(detailDTOs)
                .build();
    }
}
