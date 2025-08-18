package com.eewms.services;

import com.eewms.dto.UserDTO;
import com.eewms.entities.User;
import com.eewms.entities.Role;
import com.eewms.dto.UserProfileDTO;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface IUserService {

    List<User> findAllUsers();

    Optional<User> findUserById(Long id);

    User saveUser(User user);

    void toggleEnabledStatus(Long id);

    boolean existsByUsername(String username);

    List<Role> getAllRoles();

    Optional<User> findByUsername(String username);

    void changePassword(String username, String oldPassword, String newPassword);

    void updateUserProfile(String username, UserProfileDTO dto);


    Page<User> findAllUsersPaginated(Pageable pageable);

    Page<UserDTO> searchUsers(int page, String keyword);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    /**
     * Public flow (forgot-password):
     * - Tìm user theo email
     * - Sinh token RESET_PASSWORD
     * - Gửi email kèm link reset
     */
    void initiatePasswordResetByEmail(String email);

    /**
     * Admin flow:
     * - Tìm user theo ID
     * - Sinh token RESET_PASSWORD
     * - Gửi email kèm link reset
     */
    void initiatePasswordResetForUserId(Long userId);

    /**
     * Cập nhật mật khẩu mới cho user (BCrypt encode)
     */
    void updatePassword(Long userId, String rawPassword);
}
