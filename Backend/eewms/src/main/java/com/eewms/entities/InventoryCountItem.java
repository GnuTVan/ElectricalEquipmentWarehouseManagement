package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_count_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCountItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;   // đổi từ Long -> Integer

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_count_id")
    private InventoryCount inventoryCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer expectedQty;  // tồn hệ thống
    private Integer countedQty;   // số đếm thực tế
    private Integer variance;     // chênh lệch
    private String note;
}
