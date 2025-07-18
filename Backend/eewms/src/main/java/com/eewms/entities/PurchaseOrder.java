package com.eewms.entities;

import com.eewms.constant.PurchaseOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ví dụ: P00001, P00002,...
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.CHO_GIAO_HANG;

    @Column(columnDefinition = "TEXT")
    private String note;

    // Tổng tiền đơn hàng
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    // URL chứng từ đính kèm
    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    // Gán createdAt khi tạo
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
