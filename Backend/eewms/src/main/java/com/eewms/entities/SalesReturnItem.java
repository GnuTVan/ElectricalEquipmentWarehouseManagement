package com.eewms.entities;

import com.eewms.constant.ReturnReason;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "sales_return_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalesReturnItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_return_id", nullable = false)
    private SalesReturn salesReturn;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineAmount;

    @Column(length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private ReturnReason reason; // HANG_LOI | HANG_HONG
}
