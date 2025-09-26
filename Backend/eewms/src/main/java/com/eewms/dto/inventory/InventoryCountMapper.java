package com.eewms.dto.inventory;

import com.eewms.dto.*;
import com.eewms.entities.*;

import java.util.stream.Collectors;

public class InventoryCountMapper {
    public static InventoryCountDTO toDTO(InventoryCount entity) {
        if (entity == null) return null;

        return InventoryCountDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .status(entity.getStatus())
                .warehouseId(entity.getWarehouse().getId())
                .warehouseName(entity.getWarehouse().getName())
                .staffId(entity.getAssignedStaff() != null ? entity.getAssignedStaff().getId() : null)
                .staffName(entity.getAssignedStaff() != null ? entity.getAssignedStaff().getFullName() : null)
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .items(entity.getItems().stream()
                        .map(InventoryCountMapper::toItemDTO)
                        .toList())
                // 👇 fix ở đây
                .totalExpected(entity.getItems() != null
                        ? entity.getItems().stream()
                        .mapToInt(i -> i.getExpectedQty() != null ? i.getExpectedQty() : 0)
                        .sum()
                        : 0)
                .totalVariance(entity.getItems() != null
                        ? entity.getItems().stream()
                        .mapToInt(i -> i.getVariance() != null ? i.getVariance() : 0)
                        .sum()
                        : 0)
                .build();

    }


    public static InventoryCountItemDTO toItemDTO(InventoryCountItem item) {
        if (item == null) return null;
        return InventoryCountItemDTO.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productCode(item.getProduct() != null ? item.getProduct().getCode() : null)
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .expectedQty(item.getExpectedQty())
                .countedQty(item.getCountedQty())
                .variance(item.getVariance())
                .note(item.getNote())
                .build();
    }
}
