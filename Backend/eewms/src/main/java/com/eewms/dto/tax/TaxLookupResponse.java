package com.eewms.dto.tax;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxLookupResponse {
    private boolean found;   // true nếu tìm thấy
    private String message;  // thông báo ngắn cho FE
    private BizInfo data;    // thông tin DN (null nếu không tìm thấy)
}
