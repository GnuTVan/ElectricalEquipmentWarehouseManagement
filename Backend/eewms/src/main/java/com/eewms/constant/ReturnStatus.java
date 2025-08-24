package com.eewms.constant;

public enum ReturnStatus {
    MOI_TAO,        // staff tạo draft
    CHO_DUYET,      // chờ manager duyệt
    DA_DUYET,       // đã duyệt (chờ nhận hàng về kho)
    TU_CHOI,        // từ chối (có lý do)
    DA_NHAP_KHO,    // đã nhập hàng hoàn
    HOAN_TAT        // kết thúc quy trình
}