package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(
        name = "debt_payments",
        indexes = {
                @Index(name = "idx_debt_payment_debt", columnList = "debt_id"),
                @Index(name = "idx_debt_payment_date", columnList = "paymentDate"),
                @Index(name = "idx_debt_payment_reference_no", columnList = "reference_no")
        },
        uniqueConstraints = {
                // NEW: không cho trùng reference_no trong cùng 1 debt
                @UniqueConstraint(name = "uk_debt_ref", columnNames = {"debt_id", "reference_no"})
        }
)
public class DebtPayment {

    public enum Method { CASH, BANK_TRANSFER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Method method;

    // Đổi tên cột để khớp unique constraint trên DB
    @Column(name = "reference_no", length = 100)
    private String referenceNo; // số UNC/chứng từ

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (paymentDate == null) paymentDate = LocalDate.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
