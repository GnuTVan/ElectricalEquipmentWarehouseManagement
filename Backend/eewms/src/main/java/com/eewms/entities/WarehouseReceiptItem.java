package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "warehouse_receipt_items")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class WarehouseReceiptItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private WarehouseReceipt warehouseReceipt;

    @ManyToOne
    private Product product;

    private Integer quantity;
}
