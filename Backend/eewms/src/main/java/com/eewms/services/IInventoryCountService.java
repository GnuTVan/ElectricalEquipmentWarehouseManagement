package com.eewms.services;

import com.eewms.dto.inventory.InventoryCountDTO;
import com.eewms.entities.User;

import java.util.List;

public interface IInventoryCountService {

    // Tạo phiếu kiểm kê mới
    InventoryCountDTO create(Integer warehouseId, Integer staffId, String note, User currentManager);

    // Lấy phiếu kiểm kê theo ID
    InventoryCountDTO getById(Integer id);

    // Lấy tất cả phiếu kiểm kê
    List<InventoryCountDTO> getAll();

    // Lưu nháp (staff nhập dở, chưa submit)
    InventoryCountDTO saveDraft(Integer id, List<Integer> countedQtys, List<String> notes);

    // Nộp kết quả (staff submit → chuyển sang REVIEW)
    InventoryCountDTO submit(Integer id, List<Integer> countedQtys, List<String> notes);

    // Manager duyệt phiếu (chuyển sang APPROVED)
    InventoryCountDTO approve(Integer id, String approveNote);

    InventoryCountDTO reopen(Integer id, String approveNote);

    // Xóa phiếu kiểm kê
    void delete(Integer id);

    // Lấy tt của Staff
    List<InventoryCountDTO> getByStaffId(Long staffId);
}
