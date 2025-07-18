package com.eewms.dto;

import lombok.*;
import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDTO {

    private Long id;

    @NotBlank(message = "Tên không được để trống")
    private String name;

    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    private String taxCode;

    private String bankName;

    @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;
}