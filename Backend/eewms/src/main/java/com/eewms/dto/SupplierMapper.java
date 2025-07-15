package com.eewms.dto;

import com.eewms.entities.Supplier;

public class SupplierMapper {

    // DTO → Entity
    public static Supplier toEntity(SupplierDTO dto) {
        if (dto == null) return null;

        return Supplier.builder()
                .id(dto.getId())
                .name(dto.getName())
                .taxCode(dto.getTaxCode())
                .bankName(dto.getBankName())
                .bankAccount(dto.getBankAccount())
                .contactName(dto.getContactName())
                .contactMobile(dto.getContactMobile())
                .address(dto.getAddress())
                .status(dto.getStatus())
                .description(dto.getDescription())
                .build();
    }

    // Entity → DTO
    public static SupplierDTO toDTO(Supplier entity) {
        if (entity == null) return null;

        SupplierDTO dto = new SupplierDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTaxCode(entity.getTaxCode());
        dto.setBankName(entity.getBankName());
        dto.setBankAccount(entity.getBankAccount());
        dto.setContactName(entity.getContactName());
        dto.setContactMobile(entity.getContactMobile());
        dto.setAddress(entity.getAddress());
        dto.setStatus(entity.getStatus());
        dto.setDescription(entity.getDescription());

        return dto;
    }
}