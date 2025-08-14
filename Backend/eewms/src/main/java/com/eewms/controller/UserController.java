package com.eewms.controller;

import com.eewms.dto.UserDTO;
import com.eewms.dto.UserMapper;
import com.eewms.entities.User;
import com.eewms.repository.RoleRepository;
import com.eewms.services.IEmailService;
import com.eewms.services.IUserService;
import com.eewms.services.IVerificationTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.stream.Collectors;

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
    public String listUsers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        Page<UserDTO> userPage = userService.searchUsers(page, keyword);
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("userPage", userPage);
        model.addAttribute("keyword", keyword);

        // Model cho modal thêm
        model.addAttribute("userDTO", new UserDTO());
        model.addAttribute("allRoles", userService.getAllRoles());
        model.addAttribute("hasAddError", false); // mặc định đóng modal

        return "user-list";
    }

    // 2. (Không dùng nếu modal) - giữ nguyên nếu cần
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        UserDTO userDTO = new UserDTO();
        userDTO.setEnabled(false);
        model.addAttribute("userDTO", userDTO);
        model.addAttribute("allRoles", userService.getAllRoles());
        return "user-form";
    }

    // 3. Tạo user (submit truyền thống)
    @PostMapping
    public String createUser(@Valid @ModelAttribute("userDTO") UserDTO userDTO,
                             BindingResult result,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             Model model,
                             RedirectAttributes redirect) {

        // validate trùng
        if (userService.existsByUsername(userDTO.getUsername())) {
            result.rejectValue("username", "error.userDTO", "Tên đăng nhập đã tồn tại");
        }
        if (userService.existsByEmail(userDTO.getEmail())) {
            result.rejectValue("email", "error.userDTO", "Email đã tồn tại");
        }

        // Có lỗi -> trả về lại view, mở modal và hiển thị lỗi
        if (result.hasErrors()) {
            Page<UserDTO> userPage = userService.searchUsers(page, keyword);
            model.addAttribute("users", userPage.getContent());
            model.addAttribute("userPage", userPage);
            model.addAttribute("keyword", keyword);
            model.addAttribute("allRoles", userService.getAllRoles());
            model.addAttribute("hasAddError", true);
            return "user-list";
        }

        // Không lỗi -> lưu và gửi mail
        try {
            userDTO.setEnabled(false);
            User user = UserMapper.toEntity(userDTO, roleRepository);
            userService.saveUser(user);

            String token = verificationTokenService.createVerificationToken(user);
            emailService.sendActivationEmail(user, token);

            redirect.addFlashAttribute("message",
                    "Tạo người dùng " + userDTO.getUsername() + " thành công. Đã gửi email kích hoạt tới: " + userDTO.getEmail());
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("message", "Lỗi khi tạo người dùng: " + e.getMessage());
            redirect.addFlashAttribute("messageType", "error");
        }

        return "redirect:/admin/users";
    }

    // 3a. Tạo user (AJAX - không reload)
    @PostMapping(
            value = "/ajax",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<?> createUserAjax(@Valid UserDTO userDTO, BindingResult result) {

        // validate trùng
        if (userService.existsByUsername(userDTO.getUsername())) {
            result.rejectValue("username", "error.userDTO", "Tên đăng nhập đã tồn tại");
        }
        if (userService.existsByEmail(userDTO.getEmail())) {
            result.rejectValue("email", "error.userDTO", "Email đã tồn tại");
        }

        // Lỗi -> trả JSON 400 cùng map lỗi theo field
        if (result.hasErrors()) {
            Map<String, String> errors = result.getFieldErrors().stream()
                    .collect(Collectors.toMap(
                            FieldError::getField,
                            FieldError::getDefaultMessage,
                            (a, b) -> a // nếu trùng field thì lấy cái đầu
                    ));
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "errors", errors
            ));
        }

        // Không lỗi -> lưu & gửi mail, trả JSON 200
        try {
            userDTO.setEnabled(false);
            User user = UserMapper.toEntity(userDTO, roleRepository);
            userService.saveUser(user);

            String token = verificationTokenService.createVerificationToken(user);
            emailService.sendActivationEmail(user, token);

            // Trả dữ liệu tối thiểu để FE có thể cập nhật bảng
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "message", "Tạo người dùng " + userDTO.getUsername() + " thành công. Đã gửi email kích hoạt.",
                    "user", Map.of(
                            "id", user.getId(),
                            "fullName", user.getFullName(),
                            "username", user.getUsername(),
                            "email", user.getEmail(),
                            "phone", user.getPhone(),
                            "address", user.getAddress(),
                            "roleNames", user.getRoles().stream()
                                    .map(r -> r.getName().replace("ROLE_", ""))
                                    .toArray(String[]::new),
                            "enabled", user.isEnabled()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "message", "Lỗi khi tạo người dùng: " + e.getMessage()
            ));
        }
    }

    // 7. Bật / Tắt trạng thái
    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            User user = userService.findUserById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
            if (!user.isEnabled()) {
                if (user.getPassword() == null || user.getPassword().isBlank()) {
                    redirect.addFlashAttribute("message",
                            "Không thể bật tài khoản " + user.getUsername() + " vì " + user.getFullName() + " chưa kích hoạt qua email.");
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
            String token = verificationTokenService.createVerificationToken(user);
            emailService.sendResetPasswordEmail(user, token);
            redirect.addFlashAttribute("message", "Đã gửi email đặt lại mật khẩu cho người dùng " + user.getUsername());
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("message", "Lỗi khi gửi email đặt lại mật khẩu: " + e.getMessage());
            redirect.addFlashAttribute("messageType", "error");
        }
        return "redirect:/admin/users";
    }
}
