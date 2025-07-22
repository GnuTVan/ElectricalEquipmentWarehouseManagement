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
//    @Pattern(regexp = NAME, message = "Tên chỉ được chứa chữ cái và khoảng trắng")
    private String fullName;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @NotBlank(message = "Mã số thuế không được để trống")
//    @Pattern(regexp = TAX_CODE, message = "Mã số thuế phải gồm 10 hoặc 13 chữ số")
    private String taxCode;

    @NotBlank(message = "Tên ngân hàng không được để trống")
    @Size(max = 100, message = "Tên ngân hàng tối đa 100 ký tự")
//    @Pattern(regexp = BANK_NAME, message = "Tên ngân hàng không hợp lệ (chỉ cho phép chữ, số và khoảng trắng)")
    private String bankName;

    @NotBlank(message = "Số điện thoại không được để trống")
//    @Pattern(regexp = PHONE, message = "Số điện thoại không hợp lệ (phải bắt đầu bằng 0 và có 10–11 chữ số)")
    private String phone;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    private Customer.CustomerStatus status = Customer.CustomerStatus.ACTIVE; // Thêm dòng này

}
