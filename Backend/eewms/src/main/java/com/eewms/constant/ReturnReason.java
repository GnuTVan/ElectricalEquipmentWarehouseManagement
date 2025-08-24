package com.eewms.constant;

import lombok.Getter;

@Getter
public enum ReturnReason {
    HANG_LOI("Hàng lỗi"),
    HANG_HONG("Hàng hỏng");

    private final String label;
    ReturnReason(String label) { this.label = label; }
}
