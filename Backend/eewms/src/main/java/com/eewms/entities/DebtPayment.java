package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "debt_payments",
        indexes = {
                @Index(name = "idx_debt_payment_debt", columnList = "debt_id"),
                @Index(name = "idx_debt_payment_date", columnList = "paymentDate"),
                @Index(name = "idx_debt_payment_reference_no", columnList = "reference_no"),
                @Index(name = "idx_debt_payment_payos_order", columnList = "payos_order_code")
        },
        uniqueConstraints = {
                // không cho trùng reference_no trong cùng 1 debt
                @UniqueConstraint(name = "uk_debt_ref", columnNames = {"debt_id", "reference_no"}),
                // đảm bảo mỗi orderCode PayOS chỉ dùng một lần
                @UniqueConstraint(name = "uk_debt_payos_order", columnNames = {"payos_order_code"})
        }
)
public class DebtPayment {

    /**
     * Phương thức thanh toán
     */
    @Getter
    public enum Method {
        CASH("Tiền mặt"),
        BANK_TRANSFER("Chuyển khoản"),
        PAYOS_QR("Chuyển khoản QR"),
        RETURN_OFFSET("Khấu trừ hoàn hàng");


        private final String label;

        Method(String label) {
            this.label = label;
        }
    }

    /**
     * Trạng thái giao dịch (áp dụng cho mọi phương thức; với CASH thường = PAID ngay)
     */
    @Getter
    public enum Status {
        PENDING("Chờ thanh toán"),
        PAID("Đã thanh toán"),
        FAILED("Thất bại"),
        CANCELED("Đã huỷ"),
        EXPIRED("Hết hạn");

        private final String label;

        Status(String label) {
            this.label = label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Status status; // NEW

    // Đổi tên cột để khớp unique constraint trên DB
    @Column(name = "reference_no", length = 100)
    private String referenceNo; // số UNC/chứng từ

    @Column(length = 500)
    private String note;

    // ======= PayOS fields (chỉ dùng khi method = PAYOS_QR) =======
    @Column(name = "payos_order_code", length = 50)
    private String payosOrderCode;

    @Column(name = "payos_checkout_url", length = 512)
    private String payosCheckoutUrl;

    @Column(name = "payos_qr_code", columnDefinition = "TEXT")
    private String payosQrCode;

    @Column(name = "payos_payment_link_id", length = 100)
    private String payosPaymentLinkId;
    // ============================================================

    @Column(length = 100)
    private String createdBy; // NEW: người tạo (nếu cần log)

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (paymentDate == null) paymentDate = LocalDate.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) {
            // Mặc định: CASH/BANK_TRANSFER -> PAID, PAYOS_QR -> PENDING
            status = (method == Method.PAYOS_QR) ? Status.PENDING : Status.PAID;
        }
        if (method == null) method = Method.CASH;
    }
}
