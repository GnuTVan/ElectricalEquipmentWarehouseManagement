package com.eewms.entities;

import com.eewms.constant.ItemOrigin;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;

@Data
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "sale_order_details")
public class SaleOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "so_detail_id")
    private Long soDetailId;

    @ManyToOne
    @JoinColumn(name = "so_id", nullable = false)
    private SaleOrder sale_order; // order link

    @ManyToOne
    @JoinColumn(name = "product", nullable = false)
    private Product product;

    @Min(value = 1, message = "Ordered quantity must be at least 1")
    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "price",nullable = false)
    private BigDecimal price;

    // NEW
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemOrigin origin = ItemOrigin.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id") // có thể null nếu là manual
    private Combo combo;
}
