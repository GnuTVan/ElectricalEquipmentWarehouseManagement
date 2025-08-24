package com.eewms.dto.returning;

import com.eewms.constant.ReturnReason;
import com.eewms.entities.*;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.List;

@UtilityClass
public class SalesReturnMapper {

    public SalesReturn toEntity(SalesReturnDTO dto, SaleOrder so, User creator, List<Product> products) {
        SalesReturn sr = SalesReturn.builder()
                .code(dto.getCode())
                .saleOrder(so)
                .createdBy(creator)
                .build();

        // an toàn phòng trường hợp builder default không áp dụng (khác version Lombok)
        if (sr.getItems() == null) sr.setItems(new java.util.ArrayList<>());
        if (sr.getReason() == null) sr.setReason(com.eewms.constant.ReturnReason.HANG_LOI);

        if (dto.getItems() != null) {
            for (SalesReturnItemDTO line : dto.getItems()) {
                if (line == null || line.getProductId() == null || line.getQuantity() == null) continue;
                Product p = products.stream()
                        .filter(pr -> pr.getId().equals(line.getProductId().intValue()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Product not found: " + line.getProductId()));

                BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice()
                        : (p.getListingPrice() != null ? p.getListingPrice() : BigDecimal.ZERO);
                int qty = Math.max(0, line.getQuantity());

                SalesReturnItem it = SalesReturnItem.builder()
                        .salesReturn(sr)
                        .product(p)
                        .quantity(qty)
                        .unitPrice(price)
                        .lineAmount(price.multiply(BigDecimal.valueOf(qty)))
                        .note(line.getNote())
                        .reason(line.getReason() == null ? com.eewms.constant.ReturnReason.HANG_LOI : line.getReason())
                        .build();
                sr.getItems().add(it);
            }
        }
        return sr;
    }

    public SalesReturnDTO toDTO(SalesReturn e) {
        return SalesReturnDTO.builder()
                .id(e.getId())
                .code(e.getCode())
                .saleOrderId(e.getSaleOrder().getSoId())
                .saleOrderCode(e.getSaleOrder().getSoCode())
                .createdBy(e.getCreatedBy() != null ? e.getCreatedBy().getFullName() : null)
                .createdAt(e.getCreatedAt())
                .status(e.getStatus())
                .reason(e.getReason())
                .managerNote(e.getManagerNote())
                .needsReplacement(e.isNeedsReplacement())
                .totalAmount(e.getTotalAmount())
                .items(e.getItems() == null ? List.of() :
                        e.getItems().stream().map(it -> SalesReturnItemDTO.builder()
                                .productId(it.getProduct().getId().longValue())
                                .productName(it.getProduct().getName())
                                .quantity(it.getQuantity())
                                .unitPrice(it.getUnitPrice())
                                .lineAmount(it.getLineAmount())
                                .reason(it.getReason())
                                .note(it.getNote())
                                .build()
                        ).toList())
                .build();
    }
}
