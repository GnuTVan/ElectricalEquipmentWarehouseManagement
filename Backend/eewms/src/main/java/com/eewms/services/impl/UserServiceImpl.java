package com.eewms.services.impl;

import com.eewms.dto.UserDTO;
import com.eewms.dto.UserMapper;
import com.eewms.dto.UserProfileDTO;
import com.eewms.entities.Role;
import com.eewms.entities.User;
import com.eewms.repository.RoleRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Optional<User> findUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public User saveUser(User user) {
        // Nếu có mật khẩu mới thì mã hóa nếu chưa được mã hóa
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            String pwd = user.getPassword();
            if (!pwd.startsWith("$2a$") && !pwd.startsWith("$2b$") && !pwd.startsWith("$2y$")) {
                user.setPassword(passwordEncoder.encode(pwd));
            }
        }
        return userRepository.save(user);
    }

    @Override
    public void toggleEnabledStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public void updateUserProfile(String username, UserProfileDTO dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // Nếu người dùng xóa nội dung -> lưu null | nếu nhập -> trim và lưu
        user.setFullName(nvlTrimToNull(dto.getFullName()));
        user.setEmail(nvlTrimToNull(dto.getEmail()));
        user.setPhone(nvlTrimToNull(dto.getPhone()));
        user.setAddress(nvlTrimToNull(dto.getAddress()));

        // Avatar chỉ cập nhật khi có giá trị (được set ở Controller sau khi upload file)
        String newAvatar = nvlTrimToNull(dto.getAvatarUrl());
        if (newAvatar != null) {
            user.setAvatarUrl(newAvatar);
        }

        userRepository.save(user);
    }

    private String nvlTrimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public Page<User> findAllUsersPaginated(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public Page<UserDTO> searchUsers(int page, String keyword) {
        Pageable pageable = PageRequest.of(page, 10);

        Page<User> users;
        if (keyword == null || keyword.trim().isEmpty()) {
            System.out.println("⚠️ Không có từ khóa, trả về tất cả.");
            users = userRepository.findAll(pageable);
        } else {
            System.out.println("✅ Gọi searchByKeyword với từ khóa: " + keyword);
            users = userRepository.searchByKeyword(keyword.trim(), pageable);
        }
        return users.map(UserMapper::toDTO);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }
}
