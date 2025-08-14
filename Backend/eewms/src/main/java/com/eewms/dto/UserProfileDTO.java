package com.eewms.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {

    // Cho phép để trống; nếu nhập thì tối đa 250 ký tự
    @Size(max = 250, message = "Họ tên tối đa 250 ký tự")
    private String fullName;

    // Cho phép để trống; nếu nhập thì phải là gmail.com và tối đa 100 ký tự
    @Pattern(
            regexp = "^$|^[\\w.%+-]+@gmail\\.com$",
            message = "Email phải có đuôi @gmail.com"
    )
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    // Cho phép để trống; nếu nhập thì theo định dạng VN
    @Pattern(
            regexp = "^$|(0\\d{9}|\\+84\\d{9})$",
            message = "Số điện thoại phải bắt đầu bằng 0 hoặc +84 và đủ 10 chữ số"
    )
    private String phone;

    @Size(max = 250, message = "Địa chỉ tối đa 250 ký tự")
    private String address;

    private String avatarUrl;
}
