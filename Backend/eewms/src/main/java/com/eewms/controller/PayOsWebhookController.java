package com.eewms.controller;

import com.eewms.services.IDebtService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.google.gson.JsonElement;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PayOsWebhookController {

    @Value("${payos.webhookSecret}")
    private String webhookSecret;

    private final IDebtService debtService;

    @GetMapping(path = {"/api/webhooks/payos", "/api/webhooks/payos/"})
    public ResponseEntity<String> health() {
        log.info("[PayOS] Webhook GET check -> ok (no update)");
        return ResponseEntity.ok("ok");
    }


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

            // 2) Lấy chữ ký: CHỈ nhận từ header chuẩn (x-payos-signature hoặc x-signature)
            String usedSig = (sigPayos != null && !sigPayos.isBlank()) ? sigPayos.trim()
                    : (sigAlt != null && !sigAlt.isBlank()) ? sigAlt.trim()
                    : null;

            // Fallback: nếu header không có, thử lấy "signature" trong body (PayOS có thể gửi như vậy)
            if ((usedSig == null || usedSig.isBlank())) {
                String sigInBody = null;
                if (root.has("signature") && !root.get("signature").isJsonNull()) {
                    sigInBody = root.get("signature").getAsString();
                } else if (data.has("signature") && !data.get("signature").isJsonNull()) {
                    sigInBody = data.get("signature").getAsString();
                }
                if (sigInBody != null && !sigInBody.isBlank()) {
                    usedSig = sigInBody.trim();
                    log.info("[PayOS] Using signature from body field (no signature header)");
                }
            }

            // 3) Verify signature (PayOS chuẩn: HMAC_SHA256 trên canonical key=value của data; fallback JSON)
            boolean verified = false;

            if (usedSig != null && !usedSig.isBlank()) {
                // Build canonical string: sort keys alphabetically, join as key=value&key2=value2...
                Map<String, String> kv = new TreeMap<>();
                for (Map.Entry<String, JsonElement> e : data.entrySet()) {
                    JsonElement v = e.getValue();
                    if (v == null || v.isJsonNull()) continue; // bỏ field null
                    String val = v.isJsonPrimitive() ? v.getAsString() : v.toString(); // không URL-encode
                    kv.put(e.getKey(), val);
                }
                String canonical = kv.entrySet().stream()
                        .map(en -> en.getKey() + "=" + en.getValue())
                        .collect(Collectors.joining("&"));

                String expectedKvp = HmacUtils.hmacSha256Hex(webhookSecret, canonical);

                // Fallback theo biến thể JSON (một số môi trường tài liệu cũ)
                String dataJson = data.toString(); // JSON minified ổn định
                String expectedJson = HmacUtils.hmacSha256Hex(webhookSecret, dataJson);

                verified = usedSig.equalsIgnoreCase(expectedKvp) || usedSig.equalsIgnoreCase(expectedJson);

                if (!verified) {
                    log.warn("[PayOS] Invalid signature.");
                    log.warn("[PayOS] expected(HMAC[data-kvp])={}, canonical='{}'", expectedKvp, canonical);
                    log.warn("[PayOS] expected(HMAC[data-json])={}, dataJson={}", expectedJson, dataJson);
                    log.warn("[PayOS] got={}", usedSig);
                }
            } else {
                log.warn("[PayOS] No signature header/body -> will not update DB");
            }

            // 4) Rút gọn: lấy orderCode/status/txnId/code từ body
            String orderCode = (data.has("orderCode") && !data.get("orderCode").isJsonNull())
                    ? data.get("orderCode").getAsString() : null;
            String status = (data.has("status") && !data.get("status").isJsonNull())
                    ? data.get("status").getAsString() : null;
            String code = (data.has("code") && !data.get("code").isJsonNull())
                    ? data.get("code").getAsString() : null;

            // transaction id (nếu PayOS gửi) – dùng làm referenceNo
            String txnId = null;
            if (root.has("id") && !root.get("id").isJsonNull()) {
                txnId = root.get("id").getAsString();
            } else if (data.has("id") && !data.get("id").isJsonNull()) {
                txnId = data.get("id").getAsString();
            }
            //Fallback: nhiều webhook PayOS trả "reference" thay vì "id"
            if ((txnId == null || txnId.isBlank()) && data.has("reference") && !data.get("reference").isJsonNull()) {
                txnId = data.get("reference").getAsString();
            }

            // 5) Nếu chưa verify -> trả 200 nhưng bỏ qua cập nhật
            if (!verified) {
                return ResponseEntity.ok("ignored-unverified");
            }
            if (orderCode == null) {
                log.info("[PayOS] Verified but missing orderCode -> skip");
                return ResponseEntity.ok("ok");
            }

            // 6) Cập nhật theo trạng thái (chỉ dựa vào orderCode đã lưu trong DebtPayment)
            try {
                boolean paidVariantA = "PAID".equalsIgnoreCase(status);
                boolean paidVariantB = (status == null || status.isBlank()) && "00".equals(code);

                if (paidVariantA || paidVariantB) {
                    debtService.markPayOsPaymentPaid(orderCode, txnId, null); // paidDate = today
                    log.info("[PayOS] DebtPayment orderCode={} -> PAID (txnId={}, via={})",
                            orderCode, txnId, paidVariantA ? "status" : "code");
                    return ResponseEntity.ok("ok-paid");
                }

                if ("FAILED".equalsIgnoreCase(status)
                        || "CANCELLED".equalsIgnoreCase(status)
                        || "EXPIRED".equalsIgnoreCase(status)) {
                    debtService.markPayOsPaymentFailed(orderCode, status.toUpperCase());
                    log.info("[PayOS] DebtPayment orderCode={} -> {}", orderCode, status.toUpperCase());
                    return ResponseEntity.ok("ok-" + status.toLowerCase());
                }

                // các trạng thái khác (PENDING, PROCESSING, …) bỏ qua
                log.info("[PayOS] orderCode={} status={} -> ignored", orderCode, status);
                return ResponseEntity.ok("ignored-status");
            } catch (IllegalArgumentException | IllegalStateException ex) {
                log.warn("[PayOS] update error: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("bad-request");
            }
        } catch (Exception ex) {
            log.error("[PayOS] Error handling webhook", ex);
            return ResponseEntity.internalServerError().body("error");
        }
    }
}
