package com.eewms.controller;

import com.eewms.entities.User;
import com.eewms.services.IUserService;
import com.eewms.services.ImageUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IUserService userService;

    @MockBean
    private ImageUploadService imageUploadService;

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("GET /account/info hiển thị thông tin người dùng")
    public void testShowProfile() throws Exception {
        User user = new User();
        user.setUsername("admin");
        user.setFullName("nguyen van thinh");
        user.setEmail("vanthinhx2x@gmail.com");

        Mockito.when(userService.findByUsername(anyString()))
                .thenReturn(Optional.of(user));

        mockMvc.perform(get("/account/info"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/profile"))
                .andExpect(model().attributeExists("profile"));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("POST /account/update-profile với dữ liệu hợp lệ")
    public void testUpdateProfile_success() throws Exception {
        User user = new User();
        user.setUsername("admin");
        user.setEmail("vanthinhx2x@gmail.com");

        Mockito.when(userService.findByUsername(eq("admin"))).thenReturn(Optional.of(user));
        Mockito.when(userService.findByEmail(eq("vanthinhchimiu@gmail.com"))).thenReturn(Optional.empty());
        Mockito.when(userService.findByPhone(eq("0123456789"))).thenReturn(Optional.empty());

        // Fake file upload — tên param phải trùng với "avatarFile" trong controller
        MockMultipartFile avatar = new MockMultipartFile(
                "avatarFile",            // <-- phải là "avatarFile"
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                "dummy-image-content".getBytes()
        );

        mockMvc.perform(multipart("/account/update-profile")
                        .file(avatar)
                        .param("fullName", "nguyen van thinh1")
                        .param("email", "vanthinhchimiu@gmail.com")
                        .param("phone", "0123456787")
                        .param("address", "Hanoi")
                        .with(csrf()) // CSRF token cho POST
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/info"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /account/change-password hiển thị form")
    public void testShowChangePasswordForm() throws Exception {
        mockMvc.perform(get("/account/change-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/change-password"))
                .andExpect(model().attributeExists("changePasswordDTO"));
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("POST /account/change-password thành công (giả định mật khẩu đúng)")
    public void testChangePassword_success() throws Exception {
        User user = new User();
        user.setUsername("admin");
        user.setPassword("admin123");

        Mockito.when(userService.findByUsername(eq("admin"))).thenReturn(Optional.of(user));

        mockMvc.perform(post("/account/change-password")
                        .with(csrf()) // CSRF token
                        .param("oldPassword", "admin123")    // <-- controller dùng oldPassword
                        .param("newPassword", "admin1234")
                        .param("confirmPassword", "admin1234")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/change-password"));
    }
}
