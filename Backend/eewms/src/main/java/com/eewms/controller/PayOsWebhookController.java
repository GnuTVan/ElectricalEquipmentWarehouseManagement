package com.eewms.controller;

import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrder.PaymentStatus;
import com.eewms.repository.SaleOrderRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PayOsWebhookController {

    @Value("${payos.webhookSecret}")
    private String webhookSecret;

    private final SaleOrderRepository saleOrderRepository;

    @PostMapping(path = {"/api/webhooks/payos", "/api/webhooks/payos/"})
    public ResponseEntity<String> handle(
            @RequestBody String rawBody,
            @RequestHeader(name = "x-payos-signature", required = false) String sigPayos,
            @RequestHeader(name = "x-signature", required = false) String sigAlt
    ) {
        try {
            log.info("[PayOS] Webhook RAW: {}", rawBody);

            // 0) Guard thiếu secret
            if (webhookSecret == null || webhookSecret.isBlank()) {
                log.error("[PayOS] Missing webhookSecret. Set PAYOS_WEBHOOK_SECRET or payos.webhookSecret");
                return ResponseEntity.ok("ignored-misconfigured");
            }

            // 1) Parse JSON (để lấy data.*, orderCode/status)
            JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();
            JsonObject data = root.has("data") && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data")
                    : root;

            // 2) Lấy chữ ký: ưu tiên header chuẩn, sau đó header thay thế, cuối cùng (tuỳ chọn) fallback body
            String usedSig = (sigPayos != null && !sigPayos.isBlank()) ? sigPayos.trim()
                    : (sigAlt != null && !sigAlt.isBlank()) ? sigAlt.trim()
                    : null;

            if (usedSig == null) {
                if (root.has("signature") && !root.get("signature").isJsonNull()) {
                    usedSig = root.get("signature").getAsString();
                    log.warn("[PayOS] Header missing. Using body.signature (dev/test only)");
                } else if (data.has("signature") && !data.get("signature").isJsonNull()) {
                    usedSig = data.get("signature").getAsString();
                    log.warn("[PayOS] Header missing. Using data.signature (dev/test only)");
                }
            }

            // 3) Verify HMAC trên RAW BODY
            boolean verified = false;
            if (usedSig != null && !usedSig.isBlank()) {
                String computedSig = HmacUtils.hmacSha256Hex(webhookSecret, rawBody);
                verified = computedSig.equalsIgnoreCase(usedSig);
                if (!verified) {
                    log.warn("[PayOS] Invalid signature. expected={}, got={}", computedSig, usedSig);
                }
            } else {
                log.warn("[PayOS] No signature provided (header/body) -> will not update DB");
            }

            // 4) Lấy orderCode/status
            String orderCode = (data.has("orderCode") && !data.get("orderCode").isJsonNull())
                    ? data.get("orderCode").getAsString()
                    : null;

            String status = (data.has("status") && !data.get("status").isJsonNull())
                    ? data.get("status").getAsString()
                    : null;

            // 5) Nếu chưa verify -> trả 200 nhưng bỏ qua cập nhật
            if (!verified) {
                return ResponseEntity.ok("ignored-unverified");
            }

            if (orderCode == null || status == null) {
                log.info("[PayOS] Verified but missing orderCode/status -> skip");
                return ResponseEntity.ok("ok");
            }

            // 6) Cập nhật đơn hàng (idempotent)
            var saleOrderOpt = saleOrderRepository.findByPayOsOrderCode(orderCode);
            if (saleOrderOpt.isEmpty()) {
                log.warn("[PayOS] SaleOrder with PayOS orderCode {} not found", orderCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("order-not-found");
            }

            SaleOrder order = saleOrderOpt.get();
            PaymentStatus newStatus = mapPayOsStatus(status);

            if (order.getPaymentStatus() == newStatus) {
                log.info("[PayOS] Order {} already {}", orderCode, newStatus);
                return ResponseEntity.ok("already-processed");
            }

            order.setPaymentStatus(newStatus);
            saleOrderRepository.save(order);

            log.info("[PayOS] Order {} -> {}", orderCode, newStatus);
            return ResponseEntity.ok("ok");
        } catch (Exception ex) {
            log.error("[PayOS] Error handling webhook", ex);
            return ResponseEntity.internalServerError().body("error");
        }
    }

    private PaymentStatus mapPayOsStatus(String status) {
        if ("PAID".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
            return PaymentStatus.PAID;
        }
        if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
    }
}
