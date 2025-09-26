package com.eewms.constant;

import lombok.Getter;

@Getter
public enum InventoryCountStatus {
    IN_PROGRESS("Đang kiểm"),   // Staff đang nhập số liệu
    SUBMITTED("Chờ duyệt"),     // Staff đã nộp → Manager chờ duyệt
    APPROVED("Đã duyệt");       // Manager đã duyệt, hoàn tất

    private final String label;

    InventoryCountStatus(String label) {
        this.label = label;
    }
}
