package com.eewms.dto.payOS;

import lombok.*;

/**
 * Payload gửi /v2/payment-requests cho PayOS (prod: api-merchant.payos.vn)
 * Các trường bắt buộc: orderCode, amount (VND, integer), description, returnUrl, cancelUrl
 */
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayOsOrderRequest {
    private String orderCode;   // mã đơn gửi PayOS (string số)
    private Long amount;        // số tiền VND (integer)
    private String description; // nội dung hiển thị
    private String returnUrl;   // URL user quay về khi thanh toán xong
    private String cancelUrl;   // URL user quay về khi hủy/thoát
}
