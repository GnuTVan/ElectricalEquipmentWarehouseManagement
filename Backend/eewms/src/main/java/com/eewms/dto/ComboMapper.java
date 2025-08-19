package com.eewms.dto;

import com.eewms.entities.*;
import lombok.*;

import java.util.stream.Collectors;

// ComboMapper.java
public class ComboMapper {
    public static ComboDTO toDTO(Combo combo) {
        return ComboDTO.builder()
                .id(combo.getId())
                .code(combo.getCode())
                .name(combo.getName())
                .description(combo.getDescription())
                .status(combo.getStatus())
                .details(
                        combo.getDetails() == null ? java.util.List.of() :
                                combo.getDetails().stream()
                                        .map(detail -> ComboDetailDTO.builder()
                                                .comboId(combo.getId())
                                                .productId(detail.getProduct() != null ? detail.getProduct().getId() : null)
                                                .productName(detail.getProduct() != null ? detail.getProduct().getName() : null)
                                                .quantity(detail.getQuantity())
                                                .availableQuantity(detail.getProduct() != null ? detail.getProduct().getQuantity() : null)
                                                .price(detail.getProduct() != null ? detail.getProduct().getListingPrice() : null)
                                                .build())
                                        .collect(Collectors.toList())
                )
                .build();
    }
}