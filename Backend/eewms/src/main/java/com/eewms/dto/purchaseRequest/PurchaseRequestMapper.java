package com.eewms.dto.purchaseRequest;

import com.eewms.entities.*;

import java.util.List;
import java.util.stream.Collectors;

public class PurchaseRequestMapper {

    public static PurchaseRequest toEntity(PurchaseRequestDTO dto, List<Product> products, List<Supplier> suppliers) {
        PurchaseRequest request = PurchaseRequest.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .createdByName(dto.getCreatedByName())
                .status(dto.getStatus())
                .build();

        List<PurchaseRequestItem> items = dto.getItems().stream().map(i -> {
            Product product = products.stream()
                    .filter(p -> Long.valueOf(p.getId()).equals(i.getProductId()))
                    .findFirst().orElse(null);

            Supplier supplier = null;
            if (i.getSuggestedSupplierId() != null) {
                supplier = suppliers.stream()
                        .filter(s -> Long.valueOf(s.getId()).equals(i.getSuggestedSupplierId()))
                        .findFirst().orElse(null);
            }

            return PurchaseRequestItem.builder()
                    .id(i.getId())
                    .purchaseRequest(request)
                    .product(product)
                    .quantityNeeded(i.getQuantityNeeded())
                    .note(i.getNote())
                    .suggestedSupplier(supplier)
                    .build();
        }).collect(Collectors.toList());

        request.setItems(items);
        return request;
    }

    public static PurchaseRequestDTO toDTO(PurchaseRequest entity) {
        return PurchaseRequestDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .createdByName(entity.getCreatedByName())
                .createdAt(entity.getCreatedAt())
                .status(entity.getStatus())
                // ✅ Thêm saleOrderId để controller/UI dùng lại
                .saleOrderId(entity.getSaleOrder() != null ? entity.getSaleOrder().getSoId() : null)
                .items(entity.getItems().stream().map(item -> PurchaseRequestItemDTO.builder()
                        .id(item.getId())
                        .productId(Long.valueOf(item.getProduct().getId()))
                        .productName(item.getProduct().getName())
                        .quantityNeeded(item.getQuantityNeeded())
                        .suggestedSupplierId(item.getSuggestedSupplier() != null ? item.getSuggestedSupplier().getId() : null)
                        .suggestedSupplierName(item.getSuggestedSupplier() != null ? item.getSuggestedSupplier().getName() : null)
                        .note(item.getNote())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}
