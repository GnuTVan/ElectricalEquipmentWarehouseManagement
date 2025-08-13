package com.eewms.dto;

import com.eewms.entities.Customer;
import jakarta.validation.constraints.*;
import lombok.*;

import static com.eewms.exception.RegexPattern.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDTO {

    private Long id;

    @NotBlank(message = "Tên khách hàng không được để trống")
    @Size(min = 2, max = 100, message = "Tên phải từ 2 đến 100 ký tự")
    private String fullName;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Pattern(
            regexp = "^(?:$|\\d{10}(\\d{3})?)$",
            message = "Mã số thuế phải gồm 10 hoặc 13 chữ số"
    )
    private String taxCode;

    @Size(max = 100, message = "Tên ngân hàng tối đa 100 ký tự")
//    @Pattern(regexp = BANK_NAME, message = "Tên ngân hàng không hợp lệ (chỉ cho phép chữ, số và khoảng trắng)")
    private String bankName;

    @Pattern(
            regexp = "^(?:$|(?:\\+84|0)\\d{9})$",
            message = "Số điện thoại phải bắt đầu bằng +84 hoặc 0 và có đúng 10 chữ số"
    )
    private String phone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email quá dài")
    private String email;

    private Customer.CustomerStatus status = Customer.CustomerStatus.ACTIVE; // Thêm dòng này

}
