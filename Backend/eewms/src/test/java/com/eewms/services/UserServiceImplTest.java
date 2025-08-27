package com.eewms.services;

import com.eewms.dto.UserDTO;
import com.eewms.dto.UserProfileDTO;
import com.eewms.entities.User;
import com.eewms.repository.RoleRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.impl.UserServiceImpl;
import com.eewms.services.IEmailService;
import com.eewms.services.IVerificationTokenService;
import com.eewms.entities.VerificationToken.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IVerificationTokenService tokenService;
    @Mock private IEmailService emailService;

    @InjectMocks
    private UserServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("admin123"); // giả lập hash
        user.setEnabled(true);
    }

    @Test
    @DisplayName("toggleEnabledStatus: đảo cờ enabled và lưu")
    void toggleEnabledStatus_TogglesAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.toggleEnabledStatus(1L);

        assertFalse(user.isEnabled());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword: mật khẩu cũ đúng -> mã hóa mới và lưu")
    void changePassword_OldOk_SaveNew() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "admin123")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("admin1234");

        service.changePassword("admin", "old", "new");

        assertEquals("admin1234", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword: mật khẩu cũ sai -> ném lỗi, không lưu")
    void changePassword_OldWrong_Throws() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "admin12")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "wrong", "new"));
        assertTrue(ex.getMessage().contains("admin12"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUserProfile: đổi username sang trùng -> ném lỗi")
    void updateUserProfile_DuplicateUsername_Throws() {
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameAndIdNot("newuser", 1L)).thenReturn(true);

        UserProfileDTO dto = new UserProfileDTO();
        dto.setUsername("newuser");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateUserProfile("admin1", dto));
        assertTrue(ex.getMessage().toLowerCase().contains("đã tồn tại"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUserProfile: cập nhật thông tin và trim null/blank")
    void updateUserProfile_UpdateAndSave() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameAndIdNot(anyString(), anyLong())).thenReturn(false);

        UserProfileDTO dto = new UserProfileDTO();
        dto.setUsername(" newuser "); // sẽ được trim
        dto.setFullName("  Alice  ");
        dto.setEmail("  ");
        dto.setPhone(null);
        dto.setAddress(" Wonderland ");
        dto.setAvatarUrl(null); // không cập nhật avatar

        service.updateUserProfile("admin", dto);

        assertEquals("newuser", user.getUsername());
        assertEquals("Alice", user.getFullName());
        assertNull(user.getEmail(), "blank -> null");
        assertNull(user.getPhone(), "null -> null");
        assertEquals("Wonderland", user.getAddress());
        verify(userRepository).save(user);

        // Lần 2: có avatar mới
        dto.setAvatarUrl("  https://img  ");
        service.updateUserProfile("admin", dto);
        assertEquals("https://img", user.getAvatarUrl());
        verify(userRepository, times(2)).save(user);
    }

    @Test
    @DisplayName("searchUsers: không có keyword -> gọi findAll(pageable)")
    void searchUsers_NoKeyword_FindAll() {
        Page<User> page = new PageImpl<>(List.of(new User(), new User()));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<UserDTO> res = service.searchUsers(0, null);

        assertEquals(2, res.getTotalElements());
        verify(userRepository).findAll(PageRequest.of(0, 10));
        verify(userRepository, never()).searchByKeyword(anyString(), any());
    }

    @Test
    @DisplayName("searchUsers: có keyword -> trim và gọi searchByKeyword")
    void searchUsers_WithKeyword_DelegatesToSearch() {
        Page<User> page = new PageImpl<>(List.of(new User()));
        when(userRepository.searchByKeyword(eq("alice"), any(Pageable.class))).thenReturn(page);

        Page<UserDTO> res = service.searchUsers(1, "  alice  ");

        assertEquals(1, res.getTotalElements());
        verify(userRepository).searchByKeyword(eq("alice"), eq(PageRequest.of(1, 10)));
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("initiatePasswordResetByEmail: tìm thấy email -> tạo token & gửi mail")
    void initiatePasswordResetByEmail_Found_SendsMail() {
        when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
        when(tokenService.createToken(eq(user), eq(TokenType.RESET_PASSWORD), any(Duration.class))).thenReturn("tok");

        service.initiatePasswordResetByEmail("a@b.c");

        verify(tokenService).createToken(eq(user), eq(TokenType.RESET_PASSWORD), any(Duration.class));
        verify(emailService).sendResetPasswordEmail(user, "tok");
    }

    @Test
    @DisplayName("initiatePasswordResetByEmail: không tìm thấy -> không làm gì")
    void initiatePasswordResetByEmail_NotFound_NoOp() {
        when(userRepository.findByEmail("x@y.z")).thenReturn(Optional.empty());

        service.initiatePasswordResetByEmail("x@y.z");

        verify(tokenService, never()).createToken(any(), any(), any());
        verify(emailService, never()).sendResetPasswordEmail(any(), any());
    }

    @Test
    @DisplayName("initiatePasswordResetForUserId: tìm thấy userId -> tạo token & gửi mail")
    void initiatePasswordResetForUserId_Found_SendsMail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(tokenService.createToken(eq(user), eq(TokenType.RESET_PASSWORD), any(Duration.class))).thenReturn("tok2");

        service.initiatePasswordResetForUserId(1L);

        verify(tokenService).createToken(eq(user), eq(TokenType.RESET_PASSWORD), any(Duration.class));
        verify(emailService).sendResetPasswordEmail(user, "tok2");
    }

    @Test
    @DisplayName("updatePassword: user tồn tại -> mã hóa & lưu")
    void updatePassword_EncodesAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("raw")).thenReturn("$hash");

        service.updatePassword(1L, "raw");

        assertEquals("$hash", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updatePassword: user không tồn tại -> ném lỗi")
    void updatePassword_UserNotFound_Throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updatePassword(2L, "raw"));
        verify(userRepository, never()).save(any());
    }
}

