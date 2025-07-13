package com.eewms.controller;

import com.eewms.entities.User;
import com.eewms.entities.VerificationToken;
import com.eewms.services.IUserService;
import com.eewms.services.IVerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class ActivateController {

    private final IVerificationTokenService tokenService;
    private final IUserService userService;
    private final PasswordEncoder passwordEncoder;

    // Hiển thị form đổi mật khẩu khi người dùng nhấn link email
    @GetMapping("/activate")
    public String showActivationForm(@RequestParam("token") String token,
                                     Model model,
                                     RedirectAttributes redirect) {
        VerificationToken verificationToken = tokenService.getByToken(token).orElse(null);

        if (verificationToken == null || verificationToken.isUsed()
                || verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            redirect.addFlashAttribute("error", "Token không hợp lệ hoặc đã hết hạn.");
            return "redirect:/login";
        }

        model.addAttribute("token", token);
        return "activation-form";
    }

    // Xử lý đổi mật khẩu và kích hoạt tài khoản
    @PostMapping("/activate")
    public String processActivation(@RequestParam("token") String token,
                                    @RequestParam("newPassword") String newPassword,
                                    @RequestParam("confirmPassword") String confirmPassword,
                                    RedirectAttributes redirect) {

        VerificationToken verificationToken = tokenService.getByToken(token).orElse(null);

        if (verificationToken == null || verificationToken.isUsed()
                || verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            redirect.addFlashAttribute("error", "Token không hợp lệ hoặc đã hết hạn.");
            return "redirect:/login";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirect.addFlashAttribute("error", "Mật khẩu xác nhận không khớp.");
            redirect.addAttribute("token", token); // giữ token để reload lại form
            return "redirect:/activate";
        }

        User user = verificationToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEnabled(true);

        userService.saveUser(user); // đã có logic mã hóa nếu cần

        tokenService.markTokenAsUsed(verificationToken); // đánh dấu đã sử dụng

        redirect.addFlashAttribute("message", "Bạn đã kích hoạt tài khoản thành công. Vui lòng đăng nhập.");
        return "redirect:/login";
    }
}
