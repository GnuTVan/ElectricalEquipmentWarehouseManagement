package com.eewms.security;

import com.eewms.entities.Warehouse;
import com.eewms.entities.User;
import com.eewms.services.IUserService;
import com.eewms.services.IWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("warehousePermission")
@RequiredArgsConstructor
public class WarehousePermission {

    private final IUserService userService;
    private final IWarehouseService warehouseService;

    /**
     * Chỉ cho phép MANAGER là supervisor của chính kho này.
     * Dùng trong @PreAuthorize("hasRole('MANAGER') and @warehousePermission.isManagerOf(#id)")
     */
    public boolean isManagerOf(Integer warehouseId) {
        if (warehouseId == null) return false;

        // Lấy current user (đã đăng nhập)
        User current = userService.getCurrentUser();
        if (current == null || current.getId() == null) return false;

        // Lấy warehouse và supervisor
        Warehouse wh = warehouseService.getById(warehouseId);
        if (wh == null || wh.getSupervisor() == null || wh.getSupervisor().getId() == null) {
            return false;
        }

        // So sánh supervisor của kho với current user
        return wh.getSupervisor().getId().equals(current.getId());
    }
}
