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
                .details(combo.getDetails().stream()
                        .map(detail -> ComboDetailDTO.builder()
                                .productId(detail.getProduct().getId())
                                .productName(detail.getProduct().getName())
                                .quantity(detail.getQuantity())
                                .price(detail.getProduct().getListingPrice())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}