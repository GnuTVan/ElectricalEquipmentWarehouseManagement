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

    // ====== ACTIVATION ======
    @GetMapping("/activate")
    public String showActivationForm(@RequestParam("token") String token,
                                     Model model,
                                     RedirectAttributes redirect) {
        boolean ok = tokenService.isValid(token, VerificationToken.TokenType.ACTIVATION);
        if (!ok) {
            redirect.addFlashAttribute("error", "Token không hợp lệ hoặc đã hết hạn.");
            return "redirect:/login";
        }
        model.addAttribute("token", token);
        model.addAttribute("mode", "ACTIVATION");
        return "auth/activation-form";
    }

    @PostMapping("/activate")
    public String processActivation(@RequestParam("token") String token,
                                    @RequestParam("newPassword") String newPassword,
                                    @RequestParam("confirmPassword") String confirmPassword,
                                    RedirectAttributes redirect) {
        if (!newPassword.equals(confirmPassword)) {
            redirect.addFlashAttribute("error", "Mật khẩu xác nhận không khớp.");
            redirect.addAttribute("token", token);
            return "redirect:/activate";
        }

        boolean ok = tokenService.activateAccountByToken(token, newPassword);
        if (!ok) {
            redirect.addFlashAttribute("error", "Token không hợp lệ hoặc đã hết hạn.");
            return "redirect:/login";
        }

        redirect.addFlashAttribute("message", "Bạn đã kích hoạt tài khoản thành công. Vui lòng đăng nhập.");
        return "redirect:/login";
    }

    // ====== RESET PASSWORD ======
    @GetMapping("/reset-password")
    public String showResetForm(@RequestParam("token") String token, Model model, RedirectAttributes redirect) {
        boolean ok = tokenService.isValid(token, VerificationToken.TokenType.RESET_PASSWORD);
        if (!ok) {
            redirect.addFlashAttribute("error", "Liên kết không hợp lệ hoặc đã hết hạn.");
            return "redirect:/login";
        }
        model.addAttribute("token", token);
        model.addAttribute("mode", "RESET");
        return "auth/activation-form"; // tái sử dụng template
    }

    @PostMapping("/reset-password")
    public String processReset(@RequestParam("token") String token,
                               @RequestParam("newPassword") String newPassword,
                               @RequestParam("confirmPassword") String confirmPassword,
                               RedirectAttributes redirect) {
        if (!newPassword.equals(confirmPassword)) {
            redirect.addFlashAttribute("error", "Mật khẩu xác nhận không khớp.");
            redirect.addAttribute("token", token);
            return "redirect:/reset-password";
        }

        boolean ok = tokenService.resetPasswordByToken(token, VerificationToken.TokenType.RESET_PASSWORD, newPassword);
        if (!ok) {
            redirect.addFlashAttribute("error", "Liên kết không hợp lệ hoặc đã hết hạn.");
            return "redirect:/login";
        }

        redirect.addFlashAttribute("message", "Đặt lại mật khẩu thành công. Vui lòng đăng nhập.");
        return "redirect:/login";
    }
}
