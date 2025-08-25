package com.eewms.controller;

import com.eewms.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final IUserService userService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        if (logout != null) {
            model.addAttribute("message", "Bạn đã đăng xuất thành công.");
        }
        return "auth/login";
    }

// Hiển thị form quên mật khẩu
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "auth/forgot-password";
    }

    // Xử lý submit email
    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        if (email == null || email.isBlank()) {
            model.addAttribute("error", "Vui lòng nhập email hợp lệ.");
            return "auth/forgot-password";
        }
        // Không tiết lộ sự tồn tại của tài khoản
        userService.initiatePasswordResetByEmail(email.trim());
        model.addAttribute("message", "Nếu email tồn tại trong hệ thống, liên kết đặt lại đã được gửi.");
        return "auth/forgot-password";
    }
}

