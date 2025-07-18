package com.eewms.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDTO {
    private Long id;
    private String name;
    private String address;
    private String taxCode;
    private String bankName;
    private String phone;
    private String email;
}
