package com.eewms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Lấy Authentication hiện tại từ SecurityContext
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Lấy username hiện tại (thường là email/username trong hệ thống)
     */
    public static String getCurrentUsername() {
        Authentication auth = getAuthentication();
        return (auth != null) ? auth.getName() : null;
    }

    /**
     * Lấy ID của user hiện tại.
     * ⚠️ Yêu cầu UserDetails implement thêm field id.
     * Nếu chưa có thì tạm return null.
     */
    public static Long getCurrentUserId() {
        Authentication auth = getAuthentication();
        if (auth == null) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails cud) {
            return cud.getId(); // CustomUserDetails bạn phải có getter id
        }
        return null;
    }

    /**
     * Kiểm tra user hiện tại có role nhất định không
     */
    public static boolean hasRole(String role) {
        Authentication auth = getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("ROLE_" + role));
    }
}
