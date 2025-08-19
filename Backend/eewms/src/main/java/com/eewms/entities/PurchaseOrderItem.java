package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "contract_quantity", nullable = false)
    private Integer contractQuantity;

    @Column(name = "actual_quantity")
    private Integer actualQuantity;

    @Column(nullable = false)
    private BigDecimal price;

    // ✅ optimistic locking
    @Version
    @Column(name = "version")
    private Long version;
}
