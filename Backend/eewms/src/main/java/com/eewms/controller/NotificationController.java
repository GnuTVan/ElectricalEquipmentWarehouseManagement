package com.eewms.controller;

import com.eewms.dto.NotificationDTO;
import com.eewms.services.INotificationService;
import com.eewms.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;
    private final IUserService userService; // <-- thêm

    // ===== Legacy/Admin APIs =====
    @GetMapping
    public List<NotificationDTO> list(@RequestParam(defaultValue = "20") int limit) {
        return notificationService.list(limit);
    }

    @PostMapping
    public NotificationDTO create(@RequestBody NotificationDTO req) {
        return notificationService.save(req.getType(), req.getMessage());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        notificationService.delete(id);
    }

    @DeleteMapping
    public void clear() {
        notificationService.clear();
    }

    // ===== Per-user APIs =====

    /**
     * Danh sách thông báo của user hiện tại
     */
    @GetMapping("/my")
    public Page<NotificationDTO> myList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) String type) {

        Long userId = currentUserId();
        return notificationService.listMy(userId, unread, type, page, size);
    }

    /**
     * Đếm số thông báo chưa đọc (badge)
     */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        Long userId = currentUserId();
        return Map.of("count", notificationService.countUnread(userId));
    }

    /**
     * Đánh dấu một thông báo đã đọc
     */
    @PostMapping("/{userNotificationId}/read")
    public void markRead(@PathVariable Long userNotificationId) {
        Long userId = currentUserId();
        notificationService.markAsRead(userId, userNotificationId);
    }

    /**
     * Đánh dấu tất cả thông báo đã đọc
     */
    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        Long userId = currentUserId();
        int updated = notificationService.markAllAsRead(userId);
        return Map.of("updated", updated);
    }

    /**
     * Tạo thông báo và gán cho user hiện tại (dùng cho Toastr/CRUD)
     */
    @PostMapping("/log")
    public NotificationDTO logMine(@RequestBody NotificationDTO req) {
        Long userId = currentUserId();
        return notificationService.saveForUser(userId, req.getType(), req.getMessage());
    }

    @DeleteMapping("/my/{userNotificationId}")
    public ResponseEntity<Void> deleteMy(@PathVariable Long userNotificationId) {
        Long userId = currentUserId();
        boolean deleted = notificationService.deleteMy(userId, userNotificationId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }


    // ==== Helper: lấy userId từ SecurityContext, hỗ trợ cả CustomUserDetails lẫn User mặc định ====
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        Object principal = auth.getPrincipal();

        // 1) Nếu bạn có CustomUserDetails với getId()
        try {
            // dùng reflection nhẹ để tránh ép kiểu cứng
            var m = principal.getClass().getMethod("getId");
            Object v = m.invoke(principal);
            if (v instanceof Long) return (Long) v;
            if (v instanceof Number) return ((Number) v).longValue();
        } catch (NoSuchMethodException ignore) {
            // fallthrough
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read user id from principal", e);
        }

        // 2) Fallback: UserDetails mặc định -> lấy username rồi tra userId
        String username;
        if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            // có trường hợp principal là String (username)
            username = s;
        } else {
            username = null;
        }
        if (username == null) throw new IllegalStateException("Cannot resolve username from principal");

        return userService.findByUsername(username)
                .map(u -> u.getId())
                .orElseThrow(() -> new IllegalStateException("User not found for username=" + username));
    }
}
