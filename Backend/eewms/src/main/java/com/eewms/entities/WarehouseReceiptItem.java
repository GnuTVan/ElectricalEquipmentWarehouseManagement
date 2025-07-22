package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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
    @Column(nullable = false)
    private BigDecimal price; // đơn giá nhập

    @Column(nullable = false)
    private Integer actualQuantity; // số lượng thực nhập kho
}
