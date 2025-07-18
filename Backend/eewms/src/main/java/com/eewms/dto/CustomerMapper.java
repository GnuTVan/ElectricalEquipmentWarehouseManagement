package com.eewms.dto;

import com.eewms.entities.Customer;
import com.eewms.dto.CustomerDTO;

public class CustomerMapper {
    public CustomerDTO toDTO(Customer c) {
        return CustomerDTO.builder()
                .id(c.getId())
                .name(c.getFullName())
                .address(c.getAddress())
                .taxCode(c.getTaxCode())
                .bankName(c.getBankName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .build();
    }

    public Customer toEntity(CustomerDTO dto) {
        return Customer.builder()
                .id(dto.getId())
                .fullName(dto.getName())
                .address(dto.getAddress())
                .taxCode(dto.getTaxCode())
                .bankName(dto.getBankName())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .build();
    }
}
