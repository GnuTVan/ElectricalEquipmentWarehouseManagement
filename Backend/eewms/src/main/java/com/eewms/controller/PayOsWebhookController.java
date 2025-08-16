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

import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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
// === Anti-downgrade (THÊM NGAY DƯỚI ĐÂY) ===
            PaymentStatus current = order.getPaymentStatus();
            if (current == PaymentStatus.PAID && newStatus != PaymentStatus.PAID) {
                log.info("[PayOS] Skip downgrade from {} to {} (orderCode={})", current, newStatus, orderCode);
                return ResponseEntity.ok("skip-downgrade");
            }
// Tuỳ chọn: tránh FAILED -> PENDING khi webhook cũ tới muộn
            if (current == PaymentStatus.FAILED && newStatus == PaymentStatus.PENDING) {
                log.info("[PayOS] Skip downgrade from FAILED to PENDING (orderCode={})", orderCode);
                return ResponseEntity.ok("skip-downgrade");
            }

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

    @GetMapping({"/payos/return", "/payos/return/"})
    public RedirectView handleReturn(@RequestParam(required = false) String code,
                                     @RequestParam(required = false) String id,
                                     @RequestParam(required = false, defaultValue = "false") boolean cancel,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false, name = "orderCode") String orderCode,
                                     RedirectAttributes ra) {
        log.info("[PayOS] RETURN code={}, id={}, cancel={}, status={}, orderCode={}", code, id, cancel, status, orderCode);

        if (orderCode == null) {
            ra.addFlashAttribute("message", "Không có mã orderCode từ PayOS."+ orderCode + " / " + code);
            ra.addFlashAttribute("messageType", "warning");

            return new RedirectView("/sale-orders");
        }

        var soOpt = saleOrderRepository.findByPayOsOrderCode(orderCode);
        if (soOpt.isEmpty()) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn với orderCode: " + orderCode);
            return new RedirectView("/sale-orders");
        }

        var so = soOpt.get();
        var current = so.getPaymentStatus();
        PaymentStatus newStatus;

        if (cancel || "CANCELLED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            newStatus = PaymentStatus.FAILED;
        } else if ("00".equals(code) && "PAID".equalsIgnoreCase(status)) {
            newStatus = PaymentStatus.PAID;
        } else {
            newStatus = PaymentStatus.PENDING; // chờ webhook nếu chưa xác định
        }

        if (!(current == PaymentStatus.PAID && newStatus != PaymentStatus.PAID)) {
            if (newStatus != current) {
                so.setPaymentStatus(newStatus);
                saleOrderRepository.save(so);
                log.info("[PayOS] RETURN set {} -> {} (orderCode={})", current, newStatus, orderCode);
            }
        } else {
            log.info("[PayOS] RETURN skip downgrade {} -> {} (orderCode={})", current, newStatus, orderCode);
        }

        if (newStatus == PaymentStatus.PAID) {
            ra.addFlashAttribute("message", "Thanh toán thành công" + (id != null ? " (Mã GD: " + id + ")" : "") + ".");
            ra.addFlashAttribute("messageType", "success");
        } else if (newStatus == PaymentStatus.FAILED) {
            ra.addFlashAttribute("error", "Giao dịch đã bị huỷ/không thành công.");
        } else {
            ra.addFlashAttribute("info", "Thanh toán đang được xác nhận. Vui lòng đợi hệ thống cập nhật.");
        }

        return new RedirectView("/sale-orders/" + so.getSoId() + "/edit");
    }

    @GetMapping({"/payos/cancel", "/payos/cancel/"})
    public RedirectView handleCancel(@RequestParam(required = false, name = "orderCode") String orderCode,
                                     RedirectAttributes ra) {
        if (orderCode == null || orderCode.isBlank()) {
            ra.addFlashAttribute("warning", "Huỷ giao dịch – thiếu orderCode.");
            return new RedirectView("/sale-orders");
        }

        var soOpt = saleOrderRepository.findByPayOsOrderCode(orderCode);
        if (soOpt.isEmpty()) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn với orderCode: " + orderCode);
            return new RedirectView("/sale-orders");
        }

        var so = soOpt.get();
        if (so.getPaymentStatus() != PaymentStatus.PAID) {           // anti-downgrade
            if (so.getPaymentStatus() != PaymentStatus.UNPAID) {
                so.setPaymentStatus(PaymentStatus.UNPAID);
                saleOrderRepository.save(so);
                log.info("[PayOS] CANCEL -> set UNPAID (orderCode={})", orderCode);
            } else {
                log.info("[PayOS] CANCEL -> already UNPAID (orderCode={})", orderCode);
            }
        } else {
            log.info("[PayOS] CANCEL ignored, already PAID (orderCode={})", orderCode);
        }

        ra.addFlashAttribute("error", "Bạn đã huỷ giao dịch (" + orderCode + ").");
        return new RedirectView("/sale-orders/" + so.getSoId() + "/edit");
    }


}
