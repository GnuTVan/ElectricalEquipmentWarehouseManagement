package com.eewms.services.impl;

import com.eewms.dto.payOS.PayOsOrderResponse;
import com.eewms.exception.InventoryException;
import com.eewms.services.IPayOsService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import java.net.UnknownHostException;
import java.time.Duration;

@Slf4j
@Service
public class PayOsServiceImpl implements IPayOsService {

    @Value("${payos.clientId}")
    private String clientId;

    @Value("${payos.apiKey}")
    private String apiKey;

    // LƯU Ý: key cấu hình dạng kebab-case
    @Value("${payos.base-url}")
    private String baseUrl;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

    // Tuỳ chọn
    @Value("${payos.webhook-url:}")
    private String webhookUrl;

    @Value("${payos.checksum-key}")
    private String checksumKey;


    private WebClient client;

    @PostConstruct
    void init() {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(12)); // timeout TCP

        client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-client-id", clientId)
                .defaultHeader("x-api-key", apiKey)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
        log.info("[PayOS][init] baseUrl={} enabledHeaders(clientId,apiKey)=({},{})",
                baseUrl, clientId != null, apiKey != null);
    }

    // đặt trong class (field), ngay dưới khai báo WebClient:
    private final ObjectMapper om = new ObjectMapper();
    private static final int PAYOS_DESC_MAX = 25; // NEW: giới hạn mô tả PayOS

    @Override
    public PayOsOrderResponse createOrder(String orderCode, long amount, String description) {
        if (orderCode == null || orderCode.isBlank()) {
            throw new InventoryException("INVALID_REQUEST", "orderCode rỗng/không hợp lệ");
        }
        if (amount <= 0) {
            throw new InventoryException("INVALID_REQUEST", "Số tiền phải > 0");
        }
        if (description == null) description = "";
        String safeDesc = description.trim();
        if (safeDesc.length() > PAYOS_DESC_MAX) {
            log.warn("[PayOS] description too long ({}), trimming to {}", safeDesc.length(), PAYOS_DESC_MAX);
            safeDesc = safeDesc.substring(0, PAYOS_DESC_MAX);
        }

        log.info("[PayOS][createOrder][req] orderCode={} amount={} desc={}", orderCode, amount, safeDesc);


        // Ép orderCode về số theo yêu cầu PayOS
        final long orderCodeNum;
        try {
            orderCodeNum = Long.parseLong(orderCode);
        } catch (NumberFormatException ex) {
            throw new InventoryException("INVALID_REQUEST", "orderCode phải là số");
        }

// Tạo chuỗi ký chữ ký theo thứ tự alphabet của key
        String dataToSign = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + safeDesc
                + "&orderCode=" + orderCodeNum
                + "&returnUrl=" + returnUrl;

// Tạo signature HMAC-SHA256
        String signature = hmacSha256(dataToSign, checksumKey);
        log.info("[PayOS][createOrder][sign] data={} signature={}", dataToSign, signature);

        try {
            // Body gửi lên theo yêu cầu PayOS
            Map<String, Object> body = new HashMap<>();
            body.put("orderCode", orderCodeNum);     // gửi số, không gửi "ORD..."
            body.put("amount", amount);
            body.put("description", safeDesc);
            body.put("returnUrl", returnUrl);
            body.put("cancelUrl", cancelUrl);
            body.put("signature", signature);        // << THÊM
            if (webhookUrl != null && !webhookUrl.isBlank()) {
                body.put("webhookUrl", webhookUrl);
            }

            // Lấy raw + log + kiểm tra HTTP code
            String raw = client.post()
                    .uri("/v2/payment-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(r -> r.bodyToMono(String.class).map(b -> {
                        log.info("[PayOS][createOrder][http] status={} raw={}", r.statusCode(), b);
                        if (r.statusCode().isError()) {
                            throw new InventoryException("PAYOS_HTTP", "PayOS HTTP " + r.statusCode().value());
                        }
                        return b;
                    }))
                    .timeout(Duration.ofSeconds(20))
                    .block();

            // Parse envelope -> map
            JsonNode root = om.readTree(raw);

            // Đọc code/desc ở root (không để null)
            String code = root.path("code").asText("");
            String desc = root.path("desc").asText("");

            // Data node
            JsonNode data = root.path("data");
            Long oc = null, amt = null;
            String dsc = null, linkId = null, link = null;

            if (data != null && data.isObject()) {
                if (data.hasNonNull("orderCode"))     oc = data.get("orderCode").asLong();
                if (data.hasNonNull("amount"))        amt = data.get("amount").asLong();
                if (data.hasNonNull("description"))   dsc = data.get("description").asText();
                if (data.hasNonNull("paymentLinkId")) linkId = data.get("paymentLinkId").asText();

                // NEW: ưu tiên checkoutUrl (schema mới), fallback paymentLink (cũ)
                String checkoutUrl = data.hasNonNull("checkoutUrl") ? data.get("checkoutUrl").asText() : null;
                String paymentLink = data.hasNonNull("paymentLink") ? data.get("paymentLink").asText() : null;
                link = (checkoutUrl != null && !checkoutUrl.isBlank()) ? checkoutUrl : paymentLink;

                // Nếu muốn hỗ trợ QR sau này:
                // String qr = data.hasNonNull("qrCode") ? data.get("qrCode").asText() : null;
            }

            // Thành công khi code == "00"
            boolean ok = "00".equals(code);

            PayOsOrderResponse resp = new PayOsOrderResponse(
                    ok, code, desc, oc, amt, dsc, linkId, link
            );

            log.info("[PayOS][createOrder][resp] success={} code={} desc={} data.orderCode={} linkId={} link={}",
                    resp.isSuccess(), resp.getCode(), resp.getDesc(),
                    resp.getOrderCode(), resp.getPaymentLinkId(), resp.getPaymentLink());

            if (!ok) {
                throw new InventoryException("PAYOS_ERROR",
                        (!desc.isBlank() ? desc : "PayOS trả lỗi code=" + code));
            }

            return resp;


        } catch (WebClientRequestException ex) {
            if (ex.getCause() instanceof UnknownHostException) {
                throw new InventoryException("PAYOS_DNS",
                        "Không resolve được api.payos.vn (DNS). Kiểm tra DNS/IPv6 hoặc mạng.");
            }
            throw new InventoryException("PAYOS_NETWORK",
                    "Không kết nối được PayOS: " + ex.getMessage());
        } catch (InventoryException ie) {
            log.warn("[PayOS][createOrder][error] {}", ie.getMessage());
            throw ie;
        } catch (Exception ex) {
            log.warn("[PayOS][createOrder][error] {}", ex.getMessage());
            throw new InventoryException("PAYOS_ERROR", "Lỗi PayOS: " + ex.getMessage());
        }
    }

    @Override
    public PayOsOrderResponse getOrder(String orderCode) {
        try {
            // Lấy RAW response + log status/body
            String raw = client.get()
                    .uri("/v2/payment-requests/{code}", orderCode)
                    .exchangeToMono(r -> r.bodyToMono(String.class).map(b -> {
                        log.info("[PayOS][getOrder][http] status={} raw={}", r.statusCode(), b);
                        if (r.statusCode().isError()) {
                            throw new InventoryException("PAYOS_HTTP", "PayOS HTTP " + r.statusCode().value());
                        }
                        return b;
                    }))
                    .timeout(Duration.ofSeconds(20))
                    .block();

            // Parse envelope -> data
            JsonNode root = om.readTree(raw);

            String code = root.path("code").asText("");
            String desc = root.path("desc").asText("");

            // Data node
            JsonNode data = root.path("data");
            Long oc = null, amt = null;
            String dsc = null, linkId = null, link = null;

            // Map các field trong data nếu có
            if (data != null && data.isObject()) {
                if (data.hasNonNull("orderCode")) oc = data.get("orderCode").asLong();
                if (data.hasNonNull("amount")) amt = data.get("amount").asLong();
                if (data.hasNonNull("description")) dsc = data.get("description").asText();
                if (data.hasNonNull("paymentLinkId")) linkId = data.get("paymentLinkId").asText();

                // PayOS trả checkoutUrl (mới) + qrCode; vẫn fallback sang paymentLink (cũ) nếu có
                String checkoutUrl = data.hasNonNull("checkoutUrl") ? data.get("checkoutUrl").asText() : null;
                String paymentLink = data.hasNonNull("paymentLink") ? data.get("paymentLink").asText() : null;
                link = (checkoutUrl != null && !checkoutUrl.isBlank()) ? checkoutUrl : paymentLink;

                // Optional: nếu PayOsOrderResponse có trường qrCode thì set thêm ở đây
                // String qr = data.hasNonNull("qrCode") ? data.get("qrCode").asText() : null;
            }

// Thành công khi code == "00"
            boolean ok = "00".equals(code);

            PayOsOrderResponse resp = new PayOsOrderResponse(
                    ok, code, desc, oc, amt, dsc, linkId, link
            );

            log.info("[PayOS][getOrder][resp] success={} code={} desc={} data.orderCode={} linkId={} link={}",
                    resp.isSuccess(), resp.getCode(), resp.getDesc(),
                    resp.getOrderCode(), resp.getPaymentLinkId(), resp.getPaymentLink());

            if (!ok) {
                throw new InventoryException("PAYOS_ERROR",
                        (!desc.isBlank() ? desc : "PayOS trả lỗi code=" + code));
            }

            return resp;

        } catch (WebClientRequestException ex) {
            if (ex.getCause() instanceof UnknownHostException) {
                throw new InventoryException("PAYOS_DNS", "Không resolve được api.payos.vn (DNS).");
            }
            throw new InventoryException("PAYOS_NETWORK", "Không kết nối được PayOS: " + ex.getMessage());
        } catch (InventoryException ie) {
            log.warn("[PayOS][getOrder][error] {}", ie.getMessage());
            throw ie;
        } catch (Exception ex) {
            log.warn("[PayOS][getOrder][error] {}", ex.getMessage());
            throw new InventoryException("PAYOS_ERROR", "Lỗi PayOS: " + ex.getMessage());
        }
    }

    private String hmacSha256(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new InventoryException("SIGN_ERROR", "Không tạo được chữ ký: " + e.getMessage());
        }
    }

}
