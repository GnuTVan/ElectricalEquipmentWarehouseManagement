package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "warehouse_receipts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class WarehouseReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, unique = true)
    private String code; // VD: RN00001

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 100, nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    // ✅ Kho đích: cho phép null (soft remove)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = true)
    private Warehouse warehouse;

    @Column(columnDefinition = "TEXT")
    private String note;

    // ✅ Idempotency cho mỗi đợt nhập
    @Column(name = "request_id", length = 64, unique = true)
    private String requestId;
}

