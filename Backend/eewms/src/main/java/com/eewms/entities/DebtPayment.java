package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "debt_payments",
        indexes = {
                @Index(name = "idx_debt_payment_debt", columnList = "debt_id"),
                @Index(name = "idx_debt_payment_date", columnList = "paymentDate")
        })
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

    @Column(length = 100)
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
