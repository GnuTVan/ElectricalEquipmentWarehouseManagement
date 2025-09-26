package com.eewms.entities;

import com.eewms.constant.InventoryCountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inventory_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;   // đổi từ Long -> Integer

    private String code; // INV-YYYYMM-XXXX

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private InventoryCountStatus status = InventoryCountStatus.IN_PROGRESS;

    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "inventoryCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryCountItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (status == null) this.status = InventoryCountStatus.IN_PROGRESS;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
