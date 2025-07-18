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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mỗi item thuộc về 1 đơn hàng
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    // Sản phẩm được nhập
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "contract_quantity", nullable = false)
    private Integer contractQuantity; // số lượng theo đơn đặt

    @Column(name = "actual_quantity")
    private Integer actualQuantity; // số lượng thực nhận (null ban đầu)

    @Column(nullable = false)
    private BigDecimal price; // đơn giá nhập
}
