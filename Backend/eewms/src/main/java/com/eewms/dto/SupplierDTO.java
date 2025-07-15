package com.eewms.dto;

import lombok.Data;

@Data
public class SupplierDTO {
    private Long id;
    private String name;
    private String taxCode;
    private String bankName;
    private String bankAccount;
    private String contactName;
    private String contactMobile;
    private String address;
    private Boolean status;
    private String description;
}
