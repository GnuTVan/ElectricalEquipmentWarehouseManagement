package com.eewms.services;

import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.GoodIssueNote;

import java.util.List;

public interface IGoodIssueService {

    /**
     * Lập phiếu xuất kho từ một đơn bán (entity đã load).
     * @param order    Đơn bán (cần có soId và details)
     * @param username Người thực hiện
     * @return Phiếu xuất đã lưu
     */
    GoodIssueNote createFromOrder(SaleOrder order, String username);

    GoodIssueNoteDTO getById(Long id);

    List<GoodIssueNoteDTO> getAllNotes();

    // === BỔ SUNG: Chuẩn bị DTO để hiển thị form tạo phiếu xuất từ đơn bán (preview, chưa lưu) ===
    GoodIssueNoteDTO prepareFromSaleOrder(SaleOrder order);
}
