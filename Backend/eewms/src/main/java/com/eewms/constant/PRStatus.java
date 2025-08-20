package com.eewms.constant;

import lombok.Getter;

@Getter
public enum PRStatus {
    MOI_TAO("Mới tạo"),     // Nhân viên vừa tạo yêu cầu
    DA_DUYET("Đã duyệt"),    // Admin/Manager đã duyệt yêu cầu
    DA_TAO_PO("Đã tạo đơn mua"),   // Đã tạo PO từ yêu cầu này
    HUY("Hủy")          // Đã hủy kèm lý do
    ;
    private final String label;
    PRStatus(String label) {
        this.label = label;
    }
}
