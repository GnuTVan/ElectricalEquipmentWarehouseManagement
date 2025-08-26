package com.eewms.constant;

import lombok.Getter;

@Getter
public enum ReturnStatus {
    MOI_TAO("Mới tạo", "bg-gray-100 text-gray-700"),        // staff tạo draft
    CHO_DUYET("Chờ duyệt", "bg-amber-100 text-amber-800"),      // chờ manager duyệt
    DA_DUYET("Đã duyệt", "bg-green-100 text-green-700"),       // đã duyệt (chờ nhận hàng về kho)
    TU_CHOI("Từ chối", "bg-red-100 text-red-700"),        // từ chối (có lý do)
    DA_NHAP_KHO("Đã nhập kho", "bg-sky-100 text-sky-700"),    // đã nhập hàng hoàn
    HOAN_TAT("Hoàn tất", "bg-emerald-300 text-emerald-900");       // hoàn tất quy trình;
    private final String label;
    private final String css;


    ReturnStatus(String label, String css) {
        this.label = label;
        this.css = css;

    }// kết thúc quy trình
}