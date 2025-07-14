package com.eewms.controller;

import com.eewms.dto.ChangePasswordDTO;
import com.eewms.dto.UserProfileDTO;
import com.eewms.entities.User;
import com.eewms.services.IUserService;
import com.eewms.services.ImageUploadService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

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
    public String updateProfile(@Valid @ModelAttribute("profile") UserProfileDTO profileDTO,
                                BindingResult result,
                                @RequestParam("avatarFile") MultipartFile avatarFile,
                                RedirectAttributes redirect,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpSession session,
                                Model model) {

        // Lấy user hiện tại
        String currentUsername = userDetails.getUsername();
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // 🔍 Kiểm tra email đã tồn tại chưa (phải khác user hiện tại)
        Optional<User> userByEmail = userService.findByEmail(profileDTO.getEmail());
        if (userByEmail.isPresent() && !userByEmail.get().getUsername().equals(currentUsername)) {
            result.rejectValue("email", "error.profileDTO", "Email đã được sử dụng bởi người khác");
        }

        // 🔍 Kiểm tra phone đã tồn tại chưa (phải khác user hiện tại)
        if (profileDTO.getPhone() != null && !profileDTO.getPhone().isBlank()) {
            Optional<User> userByPhone = userService.findByPhone(profileDTO.getPhone());
            if (userByPhone.isPresent() && !userByPhone.get().getUsername().equals(currentUsername)) {
                result.rejectValue("phone", "error.profileDTO", "Số điện thoại đã được sử dụng");
            }
        }

        // 🔍 Kiểm tra lỗi validate từ annotation
        if (result.hasErrors()) {
            model.addAttribute("profile", profileDTO);
            return "profile"; // quay lại form
        }

        try {
            // ✅ Nếu có file avatar
            if (!avatarFile.isEmpty()) {
                // Kiểm tra định dạng file là ảnh
                if (!avatarFile.getContentType().startsWith("image/")) {
                    result.rejectValue("avatarUrl", "error.profileDTO", "File avatar phải là ảnh");
                    model.addAttribute("profile", profileDTO);
                    return "profile";
                }

                String imageUrl = imageUploadService.uploadImage(avatarFile);
                profileDTO.setAvatarUrl(imageUrl);
            }

            // ✅ Cập nhật hồ sơ
            userService.updateUserProfile(currentUsername, profileDTO);

            // ✅ Cập nhật timestamp avatar (để reload ảnh)
            session.setAttribute("avatarTimestamp", System.currentTimeMillis());

            redirect.addFlashAttribute("message", "Cập nhật hồ sơ thành công.");
            return "redirect:/account/info";

        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi khi cập nhật hồ sơ: " + e.getMessage());
            return "redirect:/account/info";
        }
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