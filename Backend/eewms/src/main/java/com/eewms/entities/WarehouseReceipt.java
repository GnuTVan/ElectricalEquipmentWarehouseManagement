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

    private String code; // VD: RN00001

    private LocalDateTime createdAt;

    private String createdBy;

    @ManyToOne
    private PurchaseOrder purchaseOrder;

    @ManyToOne
    private Warehouse warehouse;

    private String note;
}

