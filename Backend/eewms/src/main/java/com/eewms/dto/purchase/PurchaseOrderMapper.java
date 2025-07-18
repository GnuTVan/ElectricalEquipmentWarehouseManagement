package com.eewms.dto.purchase;

import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.PurchaseOrderItem;
import com.eewms.entities.Product;
import com.eewms.entities.Supplier;

import java.util.List;
import java.util.stream.Collectors;

public class PurchaseOrderMapper {

    // DTO → Entity
    public static PurchaseOrder toEntity(PurchaseOrderDTO dto, Supplier supplier, String attachmentUrl) {
        return PurchaseOrder.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .supplier(supplier)
                .createdByName(dto.getCreatedByName())
                .note(dto.getNote())
                .attachmentUrl(attachmentUrl)
                .build();
    }

    public static List<PurchaseOrderItem> toItemEntities(List<PurchaseOrderItemDTO> itemDTOs, PurchaseOrder order, List<Product> products) {
        return itemDTOs.stream().map(dto -> {
            Product product = products.stream()
                    .filter(p -> p.getId().equals(dto.getProductId()))
                    .findFirst().orElse(null);

            return PurchaseOrderItem.builder()
                    .purchaseOrder(order)
                    .product(product)
                    .contractQuantity(dto.getContractQuantity())
                    .actualQuantity(dto.getActualQuantity())
                    .price(dto.getPrice())
                    .build();
        }).collect(Collectors.toList());
    }

    // Entity → DTO
    public static PurchaseOrderDTO toDTO(PurchaseOrder order) {
        return PurchaseOrderDTO.builder()
                .id(order.getId())
                .code(order.getCode())
                .supplierId(order.getSupplier() != null ? order.getSupplier().getId() : null)
                .supplierName(order.getSupplier() != null ? order.getSupplier().getName() : null)
                .createdByName(order.getCreatedByName())
                .note(order.getNote())
                .attachmentUrl(order.getAttachmentUrl())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .totalAmount(order.getTotalAmount())
                .build();
    }
}

