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

    @NotBlank(message = "Mã số thuế không được để trống")
    @Size(max = 50, message = "Mã số thuế quá dài")
    private String taxCode; // ✅ Sẽ kiểm tra trùng trong Controller

    @Size(max = 100, message = "Tên ngân hàng quá dài")
    @NotBlank(message = "Vui lòng chọn tên ngân hàng")
    private String bankName; // ✅ Sẽ kiểm tra tồn tại ngân hàng trong Controller

    @Size(max = 50, message = "Số tài khoản quá dài")
    @Pattern(regexp = "\\d{8,20}", message = "Số tài khoản phải là chuỗi số từ 8 đến 20 ký tự")
    private String bankAccount; // ✅ Sẽ kiểm tra hợp lệ theo ngân hàng trong Controller

    @NotBlank(message = "Tên người liên hệ không được để trống")
    @Size(max = 100, message = "Tên người liên hệ quá dài")
    private String contactName;

    @Pattern(
            regexp = "^(\\+84|0)\\d{9}$",
            message = "Số điện thoại phải bắt đầu bằng +84 hoặc 0 và có đúng 10 chữ số"
    )
    private String contactMobile;

    @Size(max = 250, message = "Địa chỉ quá dài")
    private String address;

    private Boolean status = true;

    @Size(max = 250, message = "Ghi chú quá dài")
    private String description;
}
