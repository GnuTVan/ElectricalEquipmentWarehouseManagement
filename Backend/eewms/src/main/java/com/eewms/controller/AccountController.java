package com.eewms.controller;

import com.eewms.dto.ChangePasswordDTO;
import com.eewms.dto.UserProfileDTO;
import com.eewms.entities.User;
import com.eewms.services.IUserService;
import com.eewms.services.ImageUploadService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final IUserService userService;
    private final ImageUploadService imageUploadService;

    @GetMapping("/info")
    public String showProfile(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        UserProfileDTO profileDTO = UserProfileDTO.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .avatarUrl(user.getAvatarUrl())
                .build();

        model.addAttribute("profile", profileDTO);
        return "profile";
    }

    @PostMapping("/update-profile")
    public String updateProfile(@ModelAttribute("profile") UserProfileDTO profileDTO,
                                @RequestParam("avatarFile") MultipartFile avatarFile,
                                RedirectAttributes redirect,
                                @AuthenticationPrincipal UserDetails userDetails, HttpSession session) {
        try {
            if (!avatarFile.isEmpty()) {
                String imageUrl = imageUploadService.uploadImage(avatarFile);
                profileDTO.setAvatarUrl(imageUrl);
            }

            userService.updateUserProfile(userDetails.getUsername(), profileDTO);
            // ✅ Save timestamp vào session
            long timestamp = System.currentTimeMillis();
            session.setAttribute("avatarTimestamp", timestamp);
            //
            redirect.addFlashAttribute("message", "Cập nhật hồ sơ thành công.");

        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi khi cập nhật hồ sơ: " + e.getMessage());
        }

        return "redirect:/account/info";
    }

    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("changePasswordDTO", new ChangePasswordDTO());
        return "change-password";
    }

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
    @ModelAttribute("loggedInAvatarUrl")
    public String getAvatarUrl(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return null;

        return userService.findByUsername(userDetails.getUsername())
                .map(User::getAvatarUrl)
                .orElse(null);
    }
    @ModelAttribute("avatarTimestamp")
    public long avatarTimestamp() {
        return System.currentTimeMillis();
    }


}