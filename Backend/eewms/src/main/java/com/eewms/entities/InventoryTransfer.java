package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "inventory_transfers",
        indexes = {
                @Index(name = "idx_invtrf_code", columnList = "code", unique = true),
                @Index(name = "idx_invtrf_from_wh", columnList = "from_warehouse_id"),
                @Index(name = "idx_invtrf_to_wh", columnList = "to_warehouse_id"),
                @Index(name = "idx_invtrf_status", columnList = "status"),
                @Index(name = "idx_invtrf_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransfer {

    @Getter
    public enum Status {
        DRAFT("Chờ duyệt"),
        FROM_APPROVED("Kho nguồn đã duyệt"),
        TO_APPROVED("Kho đích đã duyệt"),
        EXPORTED("Đã xuất kho"),
        IMPORTED("Hoàn thành"),
        CANCELED("Hủy");

        private final String label;

        Status(String label) {
            this.label = label;
        }
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //inventory transfer code
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_warehouse_id", nullable = false)
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_warehouse_id", nullable = false)
    private Warehouse toWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    //Phê duyệt kho nguồn (Manager của kho nguồn)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_approved_by_id")
    private User fromApprovedBy;

    private LocalDateTime fromApprovedAt;

    //Phê duyệt kho đích (Manager của kho đích)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_approved_by_id")
    private User toApprovedBy;

    private LocalDateTime toApprovedAt;

    //Xuất kho nguồn (thực hiện tại kho nguồn)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exported_by_id")
    private User exportedBy;

    private LocalDateTime exportedAt;

    //Nhập kho đích (thực hiện tại kho đích)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by_id")
    private User importedBy;

    private LocalDateTime importedAt;


    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InventoryTransferItem> items = new ArrayList<>();

    @Formula("(select count(*) from inventory_transfer_items i where i.transfer_id = id)")
    private Integer itemCount;

    public Integer getItemCount() {
        return itemCount == null ? 0 : itemCount;
    }
}
