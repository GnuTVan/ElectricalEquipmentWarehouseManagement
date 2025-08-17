package com.eewms.services.impl;

import com.eewms.dto.NotificationDTO;
import com.eewms.entities.Notification;
import com.eewms.entities.User;
import com.eewms.entities.UserNotification;
import com.eewms.repository.NotificationRepository;
import com.eewms.repository.UserNotificationRepository;
import com.eewms.services.INotificationService;
import com.eewms.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements INotificationService {

    private final NotificationRepository repo;
    private final UserNotificationRepository userNotifRepo;
    private final IUserService userService;

    // ===== Legacy/Admin =====
    @Override
    public NotificationDTO save(String type, String message) {
        String t = normType(type);
        String msg = (message == null ? "" : message.trim());
        Notification n = Notification.builder()
                .type(t)
                .message(msg)
                .build();
        n = repo.save(n);
        return toDTO(n);
    }

    @Override
    public List<NotificationDTO> list(int limit) {
        int lim = Math.max(1, Math.min(200, limit)); // clamp 1..200
        return repo.findAll(PageRequest.of(0, lim, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toDTO)
                .getContent();
    }

    @Override
    public void delete(Long id) {
        if (id != null && repo.existsById(id)) {
            repo.deleteById(id);
        }
    }

    @Override
    public void clear() {
        repo.deleteAllInBatch();
    }

    // ===== Per-user APIs =====
    @Override
    public Page<NotificationDTO> listMy(Long userId, Boolean onlyUnread, String type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return userNotifRepo.findByUser(userId, Boolean.TRUE.equals(onlyUnread), type, pageable)
                .map(this::toDTO);
    }

    @Override
    public long countUnread(Long userId) {
        return userNotifRepo.countByUser_IdAndIsReadFalse(userId);
    }

    @Override
    public void markAsRead(Long userId, Long userNotificationId) {
        UserNotification un = userNotifRepo.findById(userNotificationId)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        if (!un.getUser().getId().equals(userId)) {
            throw new SecurityException("Forbidden");
        }
        if (!Boolean.TRUE.equals(un.getIsRead())) {
            un.setIsRead(true);
            un.setReadAt(LocalDateTime.now());
            userNotifRepo.save(un);
        }
    }

    @Override
    public int markAllAsRead(Long userId) {
        return userNotifRepo.markAllRead(userId, LocalDateTime.now());
    }

    @Override
    public NotificationDTO saveForUser(Long userId, String type, String message) {
        Notification n = saveInternal(type, message);
        User user = userService.findUserById(userId).orElseThrow();
        UserNotification un = UserNotification.builder()
                .user(user)
                .notification(n)
                .build();
        userNotifRepo.save(un);
        return toDTO(un);
    }

    @Override
    public NotificationDTO saveForUsers(List<Long> userIds, String type, String message) {
        Notification n = saveInternal(type, message);
        for (Long uid : userIds) {
            userService.findUserById(uid).ifPresent(u -> {
                UserNotification un = UserNotification.builder()
                        .user(u)
                        .notification(n)
                        .build();
                userNotifRepo.save(un);
            });
        }
        return toDTO(n);
    }

    @Override
    @Transactional
    public boolean deleteMy(Long userId, Long userNotificationId) {
        int affected = userNotifRepo.deleteByIdAndUserId(userNotificationId, userId);
        return affected > 0;
    }



    // ===== Helpers =====
    private String normType(String type) {
        String t = (StringUtils.hasText(type) ? type.trim().toLowerCase() : "info");
        return switch (t) {
            case "success", "info", "warning", "error" -> t;
            default -> "info";
        };
    }

    private Notification saveInternal(String type, String message) {
        Notification n = Notification.builder()
                .type(normType(type))
                .message(message == null ? "" : message.trim())
                .build();
        return repo.save(n);
    }

    private NotificationDTO toDTO(Notification n) {
        if (n == null) return null;
        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType())
                .message(n.getMessage())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private NotificationDTO toDTO(UserNotification un) {
        if (un == null) return null;
        Notification n = un.getNotification();
        return NotificationDTO.builder()
                .id(n.getId())
                .userNotificationId(un.getId())
                .type(n.getType())
                .message(n.getMessage())
                .createdAt(un.getCreatedAt())
                .read(un.getIsRead())
                .build();
    }
}
