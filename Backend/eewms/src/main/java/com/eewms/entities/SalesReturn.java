package com.eewms.entities;

import com.eewms.constant.ReturnReason;
import com.eewms.constant.ReturnSettlementOption;
import com.eewms.constant.ReturnStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "sales_returns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalesReturn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 20, unique = true, nullable = false)
    private String code; // SRN00001

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_order_id", nullable = false)
    private SaleOrder saleOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReturnStatus status = ReturnStatus.MOI_TAO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private ReturnReason reason = ReturnReason.HANG_LOI;

    @Column(name = "manager_note", length = 500)
    private String managerNote;

    @Builder.Default
    @Column(name = "needs_replacement", nullable = false)
    private boolean needsReplacement = false;

    @Builder.Default
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Builder.Default
    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesReturnItem> items = new ArrayList<>();

    @Builder.Default
    @Column(name = "replacement_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal replacementAmount = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_option", nullable = false, length = 32)
    private ReturnSettlementOption settlementOption = ReturnSettlementOption.OFFSET_THEN_REPLACE;

}
