package com.eewms.dto.VietQR;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BizInfo {
    private String taxCode;        // MST
    private String name;           // Tên doanh nghiệp/đơn vị
    private String shortName;      // Tên viết tắt (nếu có)
    private String address;        // Địa chỉ trụ sở
    private String provinceCode;   // Mã/Tên tỉnh (nếu provider có)
    private String representative; // Người đại diện (nếu có)
    private String status;         // Trạng thái hoạt động (nếu có)
}
