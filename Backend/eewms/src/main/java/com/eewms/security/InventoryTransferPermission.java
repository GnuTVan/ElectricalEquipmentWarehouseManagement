package com.eewms.security;

import com.eewms.entities.InventoryTransfer;
import com.eewms.entities.User;
import com.eewms.entities.Warehouse;
import com.eewms.services.IInventoryTransferService;
import com.eewms.services.IUserService;
import com.eewms.services.IWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Permission bean dùng cho cả @PreAuthorize (controller)
 * và sec:authorize (Thymeleaf).
 *
 * Quy ước vai trò:
 * - Staff của KHO NGUỒN -> được EXPORT.
 * - Staff của KHO ĐÍCH -> được IMPORT.
 * - Manager của KHO NGUỒN -> được duyệt FROM (approve-from).
 * - Manager của KHO ĐÍCH -> được duyệt TO (approve-to).
 */
@Component("transferPermission")
@RequiredArgsConstructor
public class InventoryTransferPermission {

    private final IUserService userService;
    private final IInventoryTransferService transferService;
    private final IWarehouseService warehouseService;

    /* ===== Helpers ===== */

    private User current() {
        return userService.getCurrentUser();
    }

    private InventoryTransfer getTr(Long id) {
        return transferService.get(id); // throw nếu không tồn tại
    }

    private boolean isStaffOf(Warehouse w, User u) {
        if (w == null || u == null || u.getId() == null) return false;
        // Staff: nằm trong bảng warehouse_staff của w
        return warehouseService
                .listStaffIds(w.getId())
                .stream()
                .anyMatch(uid -> uid.equals(u.getId().intValue()));
    }

    private boolean isManagerOf(Warehouse w, User u) {
        if (w == null || u == null || u.getId() == null) return false;
        var sup = w.getSupervisor();
        return sup != null && u.getId().equals(sup.getId());
    }

    /* ===== Check theo kho nguồn/đích ===== */

    public boolean isStaffOfFrom(Long transferId) {
        var u = current();
        var tr = getTr(transferId);
        return isStaffOf(tr.getFromWarehouse(), u);
    }

    public boolean isStaffOfTo(Long transferId) {
        var u = current();
        var tr = getTr(transferId);
        return isStaffOf(tr.getToWarehouse(), u);
    }

    public boolean isManagerOfFrom(Long transferId) {
        var u = current();
        var tr = getTr(transferId);
        return isManagerOf(tr.getFromWarehouse(), u);
    }

    public boolean isManagerOfTo(Long transferId) {
        var u = current();
        var tr = getTr(transferId);
        return isManagerOf(tr.getToWarehouse(), u);
    }

    /* ===== API cho @PreAuthorize & sec:authorize ===== */

    /** Chỉ MANAGER của kho NGUỒN mới được duyệt kho nguồn */
    public boolean canApproveFrom(Long transferId) {
        return isManagerOfFrom(transferId);
    }

    /** Chỉ MANAGER của kho ĐÍCH mới được duyệt kho đích */
    public boolean canApproveTo(Long transferId) {
        return isManagerOfTo(transferId);
    }

    /** Chỉ STAFF của kho NGUỒN mới được phép xuất kho */
    public boolean canExport(Long transferId) {
        return isStaffOfFrom(transferId);
    }

    /** Chỉ STAFF của kho ĐÍCH mới được phép nhập kho */
    public boolean canImport(Long transferId) {
        return isStaffOfTo(transferId);
    }
}
