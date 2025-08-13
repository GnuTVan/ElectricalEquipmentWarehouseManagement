package com.eewms.entities;

import com.eewms.constant.PRStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "purchase_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code; // VD: PR00001

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdByName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PRStatus status;

    @OneToMany(mappedBy = "purchaseRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseRequestItem> items;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = PRStatus.MOI_TAO;
    }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_id", unique = true)
    private SaleOrder saleOrder;
}
