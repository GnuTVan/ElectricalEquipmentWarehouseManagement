package com.eewms.dto.payOS;

import lombok.*;

/**
 * Payload gửi /v2/payment-requests cho PayOS (prod: api-merchant.payos.vn)
 * Các trường bắt buộc: orderCode, amount (VND, integer), description, returnUrl, cancelUrl
 */
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayOsOrderRequest {
    @JsonProperty("orderCode")
    private Long orderCode;           // yêu cầu dạng số theo PayOS

    @JsonProperty("amount")
    private Long amount;              // VND, bắt buộc

    @JsonProperty("description")
    private String description;       // mô tả hiển thị cho khách

    @JsonProperty("returnUrl")
    private String returnUrl;         // redirect khi thanh toán thành công

    @JsonProperty("cancelUrl")
    private String cancelUrl;         // redirect khi hủy

    @JsonProperty("webhookUrl")
    private String webhookUrl;        // có thể để null nếu dùng global webhook

    @JsonProperty("signature")
    private String signature;         // HMAC-SHA256 theo checksumKey
}
