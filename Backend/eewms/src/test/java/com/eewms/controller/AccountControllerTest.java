
package com.eewms.controller;

import com.eewms.dto.UserProfileDTO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @WithMockUser(username = "testuser")
    @DisplayName("GET /account/info hiển thị thông tin người dùng")
    public void testShowProfile() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        user.setFullName("Test User");
        user.setEmail("test@example.com");

        Mockito.when(userService.findByUsername(anyString()))
                .thenReturn(Optional.of(user));

        mockMvc.perform(get("/account/info"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/profile"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /account/update-profile với dữ liệu hợp lệ")
    public void testUpdateProfile_success() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("old@example.com");

        Mockito.when(userService.findByUsername(eq("testuser"))).thenReturn(Optional.of(user));
        Mockito.when(userService.findByEmail(eq("new@example.com"))).thenReturn(Optional.empty());

        MockMultipartFile avatarFile = new MockMultipartFile("avatarFile", "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE, "image-content".getBytes());

        mockMvc.perform(multipart("/account/update-profile")
                        .file(avatarFile)
                        .param("fullName", "Test User")
                        .param("email", "new@example.com")
                        .param("phone", "0123456789")
                        .param("address", "Hanoi")
                        .with(request -> { request.setMethod("POST"); return request; }))
                .andExpect(status().is3xxRedirection());
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
    @WithMockUser(username = "testuser")
    @DisplayName("POST /account/change-password thành công (giả định mật khẩu đúng)")
    public void testChangePassword_success() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("oldpassword");

        Mockito.when(userService.findByUsername(eq("testuser"))).thenReturn(Optional.of(user));
        // Giả định rằng controller so sánh oldPassword == user.getPassword()

        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "oldpassword")
                        .param("newPassword", "newsecurepassword")
                        .param("confirmPassword", "newsecurepassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/change-password"));
    }
}
