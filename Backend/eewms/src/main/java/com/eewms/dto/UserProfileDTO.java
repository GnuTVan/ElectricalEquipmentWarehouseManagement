package com.eewms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 250, message = "Họ tên tối đa 250 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Pattern(
            regexp = "^[\\w.%+-]+@gmail\\.com$",
            message = "Email phải có đuôi @gmail.com"
    )
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @Pattern(
            regexp = "^(0\\d{9}|\\+84\\d{9})$",
            message = "Số điện thoại phải bắt đầu bằng 0 hoặc +84 và đủ 10 chữ số"
    )
    private String phone;

    @Size(max = 250, message = "Địa chỉ tối đa 250 ký tự")
    private String address;

    private String avatarUrl;
}
