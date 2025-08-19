package com.eewms.services;

import com.eewms.dto.NotificationDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface INotificationService {
    NotificationDTO save(String type, String message);
    List<NotificationDTO> list(int limit);
    void delete(Long id);
    void clear();

    // ===== Per-user APIs (mới) =====
    /**
     * Lấy danh sách thông báo của một user (phân trang, có lọc).
     * @param userId id người dùng
     * @param onlyUnread true => chỉ lấy chưa đọc; false/null => tất cả
     * @param type lọc theo type (String), null => bỏ lọc
     * @param page trang (0-based)
     * @param size kích thước trang
     */
    Page<NotificationDTO> listMy(Long userId, Boolean onlyUnread, String type, int page, int size);

    /** Đếm số thông báo chưa đọc của user (badge). */
    long countUnread(Long userId);

    /** Đánh dấu 1 user-notification (id của bản ghi UserNotification) là đã đọc. */
    void markAsRead(Long userId, Long userNotificationId);

    /** Đánh dấu tất cả thông báo của user là đã đọc. */
    int markAllAsRead(Long userId);

    /** Tạo 1 thông báo và gán cho 1 user. */
    NotificationDTO saveForUser(Long userId, String type, String message);

    /** Tạo 1 thông báo và gán cho nhiều user. Trả về NotificationDTO của nội dung gốc. */
    NotificationDTO saveForUsers(List<Long> userIds, String type, String message);

    boolean deleteMy(Long userId, Long userNotificationId);
}
