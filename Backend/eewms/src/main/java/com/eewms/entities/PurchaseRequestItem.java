package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_needed", nullable = false)
    private Integer quantityNeeded;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_supplier_id")
    private Supplier suggestedSupplier;

    private String note;
}

