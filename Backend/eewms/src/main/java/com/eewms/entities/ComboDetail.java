package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "combo_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComboDetail {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;
}