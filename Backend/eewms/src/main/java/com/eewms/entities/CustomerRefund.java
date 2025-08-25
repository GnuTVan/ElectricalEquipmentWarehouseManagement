package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_refunds",
        indexes = {@Index(name = "idx_refund_so", columnList = "sale_order_so_id")}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRefund {

    @Getter
    public enum Method {
        CASH("Tiền mặt"),
        BANK_TRANSFER("Chuyển khoản");

        private final String label;
        Method(String label) {
            this.label = label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Khóa chuẩn theo SO (soId) để gom theo đơn bán
     */
    @Column(name = "sale_order_so_id", nullable = false)
    private Integer saleOrderSoId;

    /**
     * Mã phiếu hoàn, ví dụ SRN00025 (để tra cứu ngược)
     */
    @Column(name = "return_code", length = 32)
    private String returnCode;

    /**
     * Số tiền hoàn (dương)
     */
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /**
     * Phương thức hoàn (tuỳ bạn cho chọn ở UI sau)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 32, nullable = false)
    private Method method;

    /**
     * Số chứng từ/UNC/phiếu chi (nếu có)
     */
    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (method == null) method = Method.CASH;
    }
}