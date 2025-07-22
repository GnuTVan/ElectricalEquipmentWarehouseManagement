package com.eewms.dto;

import com.eewms.entities.Customer;
import com.eewms.dto.CustomerDTO;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {
    public CustomerDTO toDTO(Customer c) {
        return CustomerDTO.builder()
                .id(c.getId())
                .fullName(c.getFullName())
                .address(c.getAddress())
                .taxCode(c.getTaxCode())
                .bankName(c.getBankName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .status(c.getStatus())
                .build();

    }

    public Customer toEntity(CustomerDTO dto) {
        return Customer.builder()
                .id(dto.getId())
                .fullName(dto.getFullName())
                .address(dto.getAddress())
                .taxCode(dto.getTaxCode())
                .bankName(dto.getBankName())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .status(dto.getStatus() != null ? dto.getStatus() : Customer.CustomerStatus.ACTIVE)
                .build();
    }
}
