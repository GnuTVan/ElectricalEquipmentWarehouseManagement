package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sale_order_combos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleOrderCombo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "so_id", nullable = false)
    private SaleOrder saleOrder;

    @ManyToOne @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @Column(nullable = false)
    private Integer quantity = 1;
}
