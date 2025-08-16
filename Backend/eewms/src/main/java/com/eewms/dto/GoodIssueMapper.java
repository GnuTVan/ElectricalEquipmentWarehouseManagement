package com.eewms.dto;

import com.eewms.entities.GoodIssueDetail;
import com.eewms.entities.GoodIssueNote;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class GoodIssueMapper {

    public static GoodIssueDetailDTO toDetailDTO(GoodIssueDetail detail) {
        BigDecimal price = detail.getPrice() != null ? detail.getPrice() : BigDecimal.ZERO;
        long qty = detail.getQuantity() != null ? detail.getQuantity().longValue() : 0L;
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));

        return GoodIssueDetailDTO.builder()
                .productName(detail.getProduct() != null ? detail.getProduct().getName() : null)
                .quantity(detail.getQuantity())
                .price(price)
                .total(total)
                .build();
    }

    public static GoodIssueNoteDTO toNoteDTO(GoodIssueNote gin) {
        List<GoodIssueDetailDTO> detailDTOs = (gin.getDetails() == null
                ? List.<GoodIssueDetailDTO>of()
                : gin.getDetails().stream()
                .map(GoodIssueMapper::toDetailDTO)
                .collect(Collectors.toList()));

        Long ginId = gin.getGinId() != null ? gin.getGinId().longValue() : null;
        String customerName = (gin.getCustomer() != null) ? gin.getCustomer().getFullName() : null;
        String createdBy = (gin.getCreatedBy() != null) ? gin.getCreatedBy().getUsername() : null;

        // ================== Lấy thông tin đơn bán ==================
        String soCode = (gin.getSaleOrder() != null) ? gin.getSaleOrder().getSoCode() : null;
        Long soId = (gin.getSaleOrder() != null) ? gin.getSaleOrder().getId() : null;

        return GoodIssueNoteDTO.builder()
                .id(ginId)
                .code(gin.getGinCode())
                .customerName(customerName)
                .createdBy(createdBy)
                .issueDate(gin.getIssueDate())
                .description(gin.getDescription())
                .details(detailDTOs)
                .totalAmount(gin.getTotalAmount())
                .saleOrderCode(soCode)
                .saleOrderId(soId)   // NEW: đã map vào DTO
                // Các trường status / debtId / remainingAmount / hasDebt sẽ set ở Service
                .build();
    }
}
