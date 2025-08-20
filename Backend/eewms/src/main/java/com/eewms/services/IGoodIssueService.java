package com.eewms.services;

import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.GoodIssueNote;
import com.eewms.entities.SaleOrder;

import java.util.List;

public interface IGoodIssueService {
    // Luồng cũ – giữ nguyên
    GoodIssueNote createFromOrder(SaleOrder order, String username);
    GoodIssueNoteDTO getById(Long id);
    List<GoodIssueNoteDTO> getAllNotes();

    // NEW: hỗ trợ luồng tạo phiếu xuất từ SO (partial theo tồn/đã xuất)
    GoodIssueNoteDTO prepareFromSaleOrder(SaleOrder order);
    GoodIssueNote saveFromSaleOrderWithPartial(GoodIssueNoteDTO form, String username);
}
