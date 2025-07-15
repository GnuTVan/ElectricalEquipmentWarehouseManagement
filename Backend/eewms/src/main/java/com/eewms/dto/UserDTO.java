package com.eewms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private Long id;

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(max = 100, message = "Tên đăng nhập tối đa 100 ký tự")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]*$", message = "Tên đăng nhập không chứa ký tự đặc biệt")
    private String username;

    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 250, message = "Họ tên tối đa 250 ký tự")
    private String fullName;

    @Pattern(
            regexp = "^(0\\d{9}|\\+84\\d{9})$",
            message = "Số điện thoại phải bắt đầu bằng 0 hoặc +84 và đủ 10 chữ số"
    )
    private String phone;

    @Email(message = "Email không hợp lệ")
    @Pattern(
            regexp = "^[\\w.%+-]+@gmail\\.com$",
            message = "Email phải có đuôi @gmail.com"
    )
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @Size(max = 250, message = "Địa chỉ tối đa 250 ký tự")
    private String address;

    private boolean enabled;

    private List<Long> roleIds;
    private List<String> roleNames;
    private String avatarUrl;
}
