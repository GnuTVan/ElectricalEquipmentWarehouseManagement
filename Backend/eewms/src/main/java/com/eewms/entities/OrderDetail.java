package com.eewms.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Entity
@Table(name = "purchase_order_details")
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "po_detail_id")
    private Long oDetailId;

    @ManyToOne
    @JoinColumn(name = "po_id", nullable = false)
    private Order order; // order link

    @ManyToOne
    @JoinColumn(name = "product", nullable = false)
    private Product product;

    @Min(value = 1, message = "Ordered quantity must be at least 1")
    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity = 0; // Mặc định là 0 khi tạo mới

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @PrePersist
    @PreUpdate
    private void updateRemainingQuantity() {
        this.remainingQuantity = this.orderedQuantity - this.receivedQuantity;
    }
}
