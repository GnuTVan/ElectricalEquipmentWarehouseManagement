package com.eewms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierDTO {
    private Long id;

    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    @Size(max = 250, message = "Tên quá dài (tối đa 250 ký tự)")
    private String name;

    @Size(max = 50, message = "Mã số thuế quá dài")
    private String taxCode;

    @Size(max = 100, message = "Tên ngân hàng quá dài")
    private String bankName;

    @Size(max = 50, message = "Số tài khoản quá dài")
    private String bankAccount;

    @Size(max = 100, message = "Tên người liên hệ quá dài")
    private String contactName;

    @Pattern(regexp = "0\\d{9}", message = "Số điện thoại phải 10 số và bắt đầu bằng 0")
    private String contactMobile;

    @Size(max = 250, message = "Địa chỉ quá dài")
    private String address;

    private Boolean status = true;

    @Size(max = 250, message = "Ghi chú quá dài")
    private String description;

}
