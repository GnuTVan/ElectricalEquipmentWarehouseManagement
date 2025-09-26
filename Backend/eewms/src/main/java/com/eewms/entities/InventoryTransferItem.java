package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_transfer_items",
        indexes = {
                @Index(name = "idx_invtrf_item_transfer", columnList = "transfer_id"),
                @Index(name = "idx_invtrf_item_product", columnList = "product_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private InventoryTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    //Snapshot dvt tại thời điểm tạo phiếu ("cái", "kg")
    @Column(name = "unit_name", length = 50, nullable = false)
    private String unitName;

    //precision = 18: tổng số chữ số tối đa (bao gồm cả phần nguyên và phần thập phân). vd 9999999999999999.99
    //scale = 2: số chữ số phần thập phân tối đa là 2.
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;
}
