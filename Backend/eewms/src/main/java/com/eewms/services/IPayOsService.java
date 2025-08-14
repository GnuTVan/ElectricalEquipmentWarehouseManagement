package com.eewms.services;

import com.eewms.dto.payOS.PayOsOrderResponse;

import java.math.BigDecimal;

public interface IPayOsService {
    /**
     * Tạo đơn thanh toán PayOS và nhận QR
     *
     * @param amount      Số tiền cần thanh toán
     * @param description Nội dung thanh toán
     * @return PayOsOrderResponse chứa QR, link và orderCode
     */
    PayOsOrderResponse createOrder(String orderCode, long amount, String description);

    PayOsOrderResponse getOrder(String orderCode); // NEW: lấy lại QR/link theo orderCode

}
