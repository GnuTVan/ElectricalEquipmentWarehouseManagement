package com.eewms.controller;

import com.eewms.dto.ChangePasswordDTO;
import com.eewms.dto.UserProfileDTO;
import com.eewms.entities.User;
import com.eewms.services.IUserService;
import com.eewms.services.ImageUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final IUserService userService;
    private final ImageUploadService imageUploadService;

    @GetMapping("/info")
    public String showProfile(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        // Không đăng nhập -> về trang login
        if (userDetails == null) {
            return "redirect:/login";
        }

        // Có thể user đã bị đổi/xoá -> fallback về login thay vì 500
        Optional<User> opt = userService.findByUsername(userDetails.getUsername());
        if (opt.isEmpty()) {
            return "redirect:/login";
        }
        User user = opt.get();

        UserProfileDTO profileDTO = UserProfileDTO.builder()
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .avatarUrl(user.getAvatarUrl())
                .build();

        model.addAttribute("profile", profileDTO);
        return "auth/profile";
    }

    // ===== Fallback: submit truyền thống (nếu tắt JS) =====
    @PostMapping("/update-profile")
    public String updateProfile(@Valid @ModelAttribute("profile") UserProfileDTO profileDTO,
                                BindingResult result,
                                @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
                                RedirectAttributes redirect,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpSession session,
                                Model model,
                                HttpServletRequest request) {

        String currentUsername = userDetails.getUsername();
        userService.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // Chỉ kiểm tra trùng khi có nhập
        if (hasText(profileDTO.getEmail())) {
            Optional<User> byEmail = userService.findByEmail(profileDTO.getEmail());
            if (byEmail.isPresent() && !byEmail.get().getUsername().equals(currentUsername)) {
                result.rejectValue("email", "error.profileDTO", "Email đã được sử dụng bởi người khác");
            }
        }
        if (hasText(profileDTO.getPhone())) {
            Optional<User> byPhone = userService.findByPhone(profileDTO.getPhone());
            if (byPhone.isPresent() && !byPhone.get().getUsername().equals(currentUsername)) {
                result.rejectValue("phone", "error.profileDTO", "Số điện thoại đã được sử dụng");
            }
        }

        if (result.hasErrors()) {
            model.addAttribute("profile", profileDTO);
            return "auth/profile";
        }

        try {
            // Chỉ xử lý file nếu có chọn
            if (avatarFile != null && !avatarFile.isEmpty()) {
                String ct = avatarFile.getContentType();
                if (ct == null || !ct.startsWith("image/")) {
                    result.rejectValue("avatarUrl", "error.profileDTO", "File avatar phải là ảnh");
                    model.addAttribute("profile", profileDTO);
                    return "auth/profile";
                }
                String imageUrl = imageUploadService.uploadImage(avatarFile);
                profileDTO.setAvatarUrl(imageUrl);
            }
            boolean usernameChanged = profileDTO.getUsername() != null
                    && !profileDTO.getUsername().trim().equals(currentUsername);

            userService.updateUserProfile(currentUsername, profileDTO);

            if (usernameChanged) {
                try {
                    request.logout();
                } catch (Exception ignored) {
                }
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                }
                redirect.addFlashAttribute("message", "Đã đổi tên đăng nhập. Vui lòng đăng nhập lại.");
                return "redirect:/login";
            }

            session.setAttribute("avatarTimestamp", System.currentTimeMillis());
            redirect.addFlashAttribute("message", "Cập nhật hồ sơ thành công.");
            return "redirect:/account/info";

        } catch (Exception e) {
            redirect.addFlashAttribute("error",
                    "Lỗi khi cập nhật hồ sơ: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return "redirect:/account/info";
        }
    }

    // ===== AJAX: không reload trang, trả JSON =====
    @PostMapping(
            value = "/update-profile/ajax",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<?> updateProfileAjax(@Valid @ModelAttribute("profile") UserProfileDTO profileDTO,
                                               BindingResult result,
                                               @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
                                               @AuthenticationPrincipal UserDetails userDetails,
                                               HttpSession session,
                                               HttpServletRequest request) {

        String currentUsername = userDetails.getUsername();
        User current = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        String newEmail = profileDTO.getEmail();
        boolean emailChanged = newEmail != null && !newEmail.isBlank()
                && !newEmail.equalsIgnoreCase(current.getEmail());

        // Chỉ kiểm tra trùng khi có nhập
        if (emailChanged) {
            if (userService.existsByEmailIgnoreCaseAndIdNot(newEmail, current.getId())) {
                result.rejectValue("email", "error.profileDTO", "Email đã được sử dụng bởi người khác");
            }
        }
        if (hasText(profileDTO.getPhone())) {
            Optional<User> byPhone = userService.findByPhone(profileDTO.getPhone());
            if (byPhone.isPresent() && !byPhone.get().getUsername().equals(currentUsername)) {
                result.rejectValue("phone", "error.profileDTO", "Số điện thoại đã được sử dụng");
            }
        }

        if (result.hasErrors()) {
            Map<String, String> errors = result.getFieldErrors().stream()
                    .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
            return ResponseEntity.badRequest().body(Map.of("ok", false, "errors", errors));
        }

        try {
            String newAvatar = null;
            if (avatarFile != null && !avatarFile.isEmpty()) {
                String ct = avatarFile.getContentType();
                if (ct == null || !ct.startsWith("image/")) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "ok", false,
                            "errors", Map.of("avatarUrl", "File avatar phải là ảnh")
                    ));
                }
                newAvatar = imageUploadService.uploadImage(avatarFile);
                profileDTO.setAvatarUrl(newAvatar);
            }

            boolean usernameChanged = profileDTO.getUsername() != null
                    && !profileDTO.getUsername().trim().equals(currentUsername);

            userService.updateUserProfile(currentUsername, profileDTO);
            if (newAvatar != null) {
                session.setAttribute("avatarTimestamp", System.currentTimeMillis());
            }

            if (usernameChanged) {
                try {
                    request.logout();
                } catch (Exception ignored) {
                }
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                }
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "requireLogin", true,
                        "message", "Đã đổi tên đăng nhập. Vui lòng đăng nhập lại."
                ));
            }
            User refreshed = userService.findByUsername(currentUsername).orElse(current);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "requireLogin", false,
                    "message", "Cập nhật hồ sơ thành công.",
                    "profile", Map.of(
                            "username", refreshed.getUsername(),
                            "fullName", refreshed.getFullName(),
                            "email", refreshed.getEmail(),
                            "phone", refreshed.getPhone(),
                            "address", refreshed.getAddress(),
                            "avatarUrl", refreshed.getAvatarUrl()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "message", "Lỗi khi cập nhật hồ sơ: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            ));
        }
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    // ===== Đổi mật khẩu giữ nguyên =====
    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("changePasswordDTO", new ChangePasswordDTO());
        return "auth/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("changePasswordDTO") ChangePasswordDTO dto,
                                 BindingResult result,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirect,
                                 Model model) {

        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            result.rejectValue("newPassword", "error.newPassword", "Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "error.confirmPassword", "Mật khẩu xác nhận không khớp");
        }
        if (result.hasErrors()) {
            model.addAttribute("changePasswordDTO", dto);
            return "auth/change-password";
        }

        try {
            userService.changePassword(userDetails.getUsername(), dto.getOldPassword(), dto.getNewPassword());
            redirect.addFlashAttribute("message", "Đổi mật khẩu thành công.");
            return "redirect:/account/info";
        } catch (Exception e) {
            result.rejectValue("oldPassword", "error.oldPassword", e.getMessage());
            model.addAttribute("changePasswordDTO", dto);
            return "auth/change-password";
        }
    }
}
