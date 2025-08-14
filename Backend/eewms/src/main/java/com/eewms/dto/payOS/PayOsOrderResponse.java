package com.eewms.dto.payOS;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayOsOrderResponse {
    /**
     * Top-level fields theo JSON thực tế
     */
    @JsonProperty("code")
    private String code;          // "00"

    @JsonProperty("desc")
    private String desc;          // "success"

    // Không có "success" trong JSON PayOS /v2 -> để Optional cho an toàn
    private Boolean success;      // sẽ là null nếu PayOS không trả; không dùng để quyết định logic

    @JsonProperty("data")
    private DataPayload data;

    @JsonProperty("signature")
    private String signature;

    // THAY phần constructor rỗng hiện tại bằng đoạn này
    public PayOsOrderResponse(
            boolean success,
            String code,
            String desc,
            Long oc,
            Long amt,
            String dsc,
            String linkId,
            String link
    ) {
        this.success = success;
        this.code = code;
        this.desc = desc;

        DataPayload dp = new DataPayload();
        if (oc  != null) dp.setOrderCode(oc);
        if (amt != null) dp.setAmount(amt);
        if (dsc != null) dp.setDescription(dsc);
        if (linkId != null && !linkId.isBlank()) dp.setPaymentLinkId(linkId);
        if (link   != null && !link.isBlank())   dp.setCheckoutUrl(link);
        this.data = dp;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPayload {
        private String bin;

        @JsonProperty("accountNumber")
        private String accountNumber;

        @JsonProperty("accountName")
        private String accountName;

        @JsonProperty("amount")
        private long amount;

        @JsonProperty("description")
        private String description;

        @JsonProperty("orderCode")
        private long orderCode;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("paymentLinkId")
        private String paymentLinkId;   // ví dụ: 58bd0f56ac3540c38ed45829f51b20a4

        @JsonProperty("status")
        private String status;          // ví dụ: PENDING

        @JsonProperty("checkoutUrl")
        private String checkoutUrl;     // ví dụ: https://pay.payos.vn/web/<id>

        @JsonProperty("qrCode")
        private String qrCode;          // raw QR content

        // các key khác PayOS có thể trả — đã được ignoreUnknown
    }

    // ==== Convenience getters ====
    public Long getOrderCode() {
        return data != null ? data.getOrderCode() : null;
    }

    public Long getAmount() {
        return data != null ? data.getAmount() : null;
    }

    public String getDescription() {
        return data != null ? data.getDescription() : null;
    }

    public String getPaymentLinkId() {
        return data != null ? data.getPaymentLinkId() : null;
    }

    public String getCheckoutUrl() {
        return data != null ? data.getCheckoutUrl() : null;
    }

    public String getQrCode() {
        return data != null ? data.getQrCode() : null;
    }

    public String getStatus() {
        return data != null ? data.getStatus() : null;
    }

    /**
     * Payment link: ưu tiên checkoutUrl, sau đó fallback từ paymentLinkId
     */
    public String getPaymentLink() {
        if (data == null) return null;
        if (data.getCheckoutUrl() != null && !data.getCheckoutUrl().isBlank()) {
            return data.getCheckoutUrl();
        }
        String id = data.getPaymentLinkId();
        return (id == null || id.isBlank()) ? null : ("https://pay.payos.vn/web/" + id);
    }

    public boolean isSuccess() {
        // PayOS /v2 trả code="00" + data; đôi khi không có trường "success"
        boolean okByCode = "00".equals(this.code);
        boolean okByFlag = Boolean.TRUE.equals(this.success); // phòng trường hợp API khác có trả
        boolean hasData = this.data != null
                && (
                (this.data.getCheckoutUrl() != null && !this.data.getCheckoutUrl().isBlank()) ||
                        (this.data.getPaymentLinkId() != null && !this.data.getPaymentLinkId().isBlank())
        );
        return (okByCode || okByFlag) && hasData;
    }

}
