package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "debts",
        indexes = {
                @Index(name = "idx_debt_supplier", columnList = "supplier_id"),
                @Index(name = "idx_debt_due_date", columnList = "dueDate"),
                @Index(name = "idx_debt_status", columnList = "status")
        })
public class Debt {

    public enum Status { UNPAID, PARTIAL, PAID, OVERDUE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Supplier supplier;

    // tạo nợ khi xác nhận phiếu nhập
    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseReceipt warehouseReceipt;

    // tùy chọn: truy dấu ngược về PO
    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    private LocalDate invoiceDate; // = ngày tạo phiếu nhập
    private LocalDate dueDate;     // = invoiceDate + 0/7/10

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "debt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DebtPayment> payments = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;
        if (status == null) status = Status.UNPAID;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public BigDecimal getRemaining() {
        return (totalAmount == null ? BigDecimal.ZERO : totalAmount)
                .subtract(paidAmount == null ? BigDecimal.ZERO : paidAmount);
    }
}
