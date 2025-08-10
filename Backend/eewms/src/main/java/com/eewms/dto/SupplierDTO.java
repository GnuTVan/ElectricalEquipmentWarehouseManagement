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

    @Pattern(regexp = "^(?:$|\\d{10}(\\d{3})?)$",
            message = "Mã số thuế phải gồm 10 hoặc 13 chữ số")
    private String taxCode;

    @Size(max = 100, message = "Tên ngân hàng quá dài")
    private String bankName;

    @Pattern(regexp = "^(?:$|\\d{8,19})$",
            message = "Số tài khoản phải là chuỗi số từ 8–19 chữ số")
    private String bankAccount;

    @Size(max = 100, message = "Tên người liên hệ quá dài")
    private String contactName;

    @Pattern(
            regexp = "^(?:$|(?:\\+84|0)\\d{9})$",
            message = "Số điện thoại phải bắt đầu bằng +84 hoặc 0 và có đúng 10 chữ số"
    )
    private String contactMobile;

    @Size(max = 250, message = "Địa chỉ quá dài")
    private String address;

    private Boolean status = true;

    @Size(max = 250, message = "Ghi chú quá dài")
    private String description;
}