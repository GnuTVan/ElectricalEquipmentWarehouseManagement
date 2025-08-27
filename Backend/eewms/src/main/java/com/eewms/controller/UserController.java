package com.eewms.controller;

import com.eewms.dto.UserDTO;
import com.eewms.dto.UserMapper;
import com.eewms.entities.User;
import com.eewms.repository.RoleRepository;
import com.eewms.services.IUserService;
import com.eewms.services.IEmailService;
import com.eewms.services.IVerificationTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import com.eewms.entities.VerificationToken;
import java.time.Duration;


@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final RoleRepository roleRepository;
    private final IVerificationTokenService verificationTokenService;
    private final IEmailService emailService;

    // 1. Danh sách người dùng
    @GetMapping
    public String listUsers(@RequestParam(value = "page", defaultValue = "0") int page,
                            @RequestParam(value = "keyword", required = false) String keyword,
                            Model model) {
        Page<UserDTO> userPage = userService.searchUsers(page, keyword);
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("userPage", userPage);
        model.addAttribute("keyword", keyword);

        if (!model.containsAttribute("userDTO")) {
            UserDTO userDTO = new UserDTO();
            userDTO.setEnabled(false);
            model.addAttribute("userDTO", userDTO);
        }

        model.addAttribute("allRoles", userService.getAllRoles());

        // ✅ CỜ MẶC ĐỊNH: KHÔNG CÓ LỖI
        model.addAttribute("hasValidationErrors", false);

        return "user-list";
    }

    // 2. Hiển thị form tạo (không dùng nếu dùng modal)
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        UserDTO userDTO = new UserDTO();
        userDTO.setEnabled(false);

        model.addAttribute("userDTO", userDTO);
        model.addAttribute("allRoles", userService.getAllRoles());
        return "user-form";
    }

    // 3. Xử lý tạo user và gửi mail kích hoạt
    @PostMapping
    public String createUser(@Valid @ModelAttribute("userDTO") UserDTO userDTO,
                             BindingResult result,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             Model model,
                             RedirectAttributes redirect) {

        // Validate trùng username/email
        if (userService.existsByUsername(userDTO.getUsername())) {
            result.rejectValue("username", "error.userDTO", "Tên đăng nhập đã tồn tại");
        }
        if (userService.existsByEmail(userDTO.getEmail())) {
            result.rejectValue("email", "error.userDTO", "Email đã tồn tại");
        }

        // Nếu có lỗi → trả về lại view kèm cờ mở modal
        if (result.hasErrors()) {
            Page<UserDTO> userPage = userService.searchUsers(page, keyword);
            model.addAttribute("users", userPage.getContent());
            model.addAttribute("userPage", userPage);
            model.addAttribute("keyword", keyword);
            model.addAttribute("allRoles", userService.getAllRoles());

            // ✅ BÁO VIEW MỞ MODAL & BIẾT CÓ LỖI
            model.addAttribute("openCreateModal", true);
            model.addAttribute("hasValidationErrors", true);

            return "user-list";
        }

        // Không lỗi → tiến hành tạo user
        try {
            userDTO.setEnabled(false);
            User user = UserMapper.toEntity(userDTO, roleRepository);
            userService.saveUser(user);

            String token = verificationTokenService.createToken(
                    user,
                    VerificationToken.TokenType.ACTIVATION,
                    Duration.ofHours(24)
            );
            emailService.sendActivationEmail(user, token);


            redirect.addFlashAttribute("message", "Tạo người dùng " + userDTO.getUsername()
                    + " thành công. Đã gửi email kích hoạt tới: " + userDTO.getEmail());
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi khi tạo người dùng: " + e.getMessage());
        }

        return "redirect:/admin/users";
    }

    // 7. Bật / Tắt trạng thái
    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            User user = userService.findUserById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

            // Nếu đang tắt → chuẩn bị bật
            if (!user.isEnabled()) {
                if (user.getPassword() == null || user.getPassword().isBlank()) {
                    redirect.addFlashAttribute("message",
                            "Không thể bật tài khoản " + user.getUsername()
                                    + " vì người dùng " + user.getFullName() + " chưa kích hoạt qua email.");
                    redirect.addFlashAttribute("messageType", "error");
                    return "redirect:/admin/users";
                }
            }

            userService.toggleEnabledStatus(id);
            redirect.addFlashAttribute("message", "Cập nhật trạng thái tài khoản " + user.getUsername() + " thành công.");
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("message", "Lỗi khi cập nhật trạng thái: " + e.getMessage());
            redirect.addFlashAttribute("messageType", "error");
        }

        return "redirect:/admin/users";
    }

    // 8. reset password
    @PostMapping("/reset-password/{id}")
    public String resetPassword(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            User user = userService.findUserById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

            String token = verificationTokenService.createToken(
                    user,
                    VerificationToken.TokenType.RESET_PASSWORD,
                    Duration.ofHours(2)
            );
            emailService.sendResetPasswordEmail(user, token); // đảm bảo link /reset-password

            redirect.addFlashAttribute("message",
                    "Đã gửi email đặt lại mật khẩu cho người dùng " + user.getUsername());
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("message", "Lỗi khi gửi email đặt lại mật khẩu: " + e.getMessage());
            redirect.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/users";
    }
}
