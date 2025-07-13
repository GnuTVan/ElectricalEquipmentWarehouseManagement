package com.eewms.controller;

import com.eewms.dto.ChangePasswordDTO;
import com.eewms.dto.UserDTO;
import com.eewms.dto.UserMapper;
import com.eewms.dto.UserProfileDTO;
import com.eewms.entities.User;
import com.eewms.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {


    private final IUserService userService;

    // Hiển thị hồ sơ cá nhân
    @GetMapping("/info")
    public String showProfile(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        UserProfileDTO profileDTO = UserProfileDTO.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .build();

        model.addAttribute("profile", profileDTO);
        return "profile";
    }


    // Cập nhật hồ sơ cá nhân
    @PostMapping("/update-profile")
    public String updateProfile(@ModelAttribute("profile") UserProfileDTO dto,
                                RedirectAttributes redirect,
                                @AuthenticationPrincipal UserDetails userDetails) {
        try {
            userService.updateUserProfile(userDetails.getUsername(), dto);


            redirect.addFlashAttribute("message", "Cập nhật hồ sơ thành công.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi khi cập nhật hồ sơ: " + e.getMessage());
        }

        return "redirect:/account/info";
    }

    // Hiển thị form đổi mật khẩu
    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("changePasswordDTO", new ChangePasswordDTO());
        return "change-password";
    }

    // 2. Xử lý đổi mật khẩu
    @PostMapping("/change-password")
    public String changePassword(@ModelAttribute("changePasswordDTO") ChangePasswordDTO dto,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirect) {
        try {
            if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
                redirect.addFlashAttribute("error", "Mật khẩu mới và xác nhận không khớp.");
                return "redirect:/account/change-password";
            }

            userService.changePassword(userDetails.getUsername(), dto.getOldPassword(), dto.getNewPassword());
            redirect.addFlashAttribute("message", "Đổi mật khẩu thành công.");
            return "redirect:/account/info";
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/account/change-password";
        }
    }

}
