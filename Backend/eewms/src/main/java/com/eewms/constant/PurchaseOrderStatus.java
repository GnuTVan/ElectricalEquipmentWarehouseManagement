package com.eewms.constant;

import lombok.Getter;

@Getter
public enum PurchaseOrderStatus {
    CHO_DUYET("Chờ duyệt"), // Chờ duyệt
    CHO_GIAO_HANG("Chờ giao hàng"),      // Chờ giao hàng
    DA_GIAO_MOT_PHAN("Đã giao một phần"),   // Đã giao một phần
    HOAN_THANH("Hoàn thành"),         // Hoàn thành
    HUY("Hủy");             // Đã huỷ
    
    private final String label;

    PurchaseOrderStatus(String label) {
        this.label = label;
    }

}