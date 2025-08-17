package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "debts",
        indexes = {
                @Index(name = "idx_debt_party_type", columnList = "party_type"),
                @Index(name = "idx_debt_status", columnList = "status"),
                @Index(name = "idx_debt_due_date", columnList = "due_date"),
                @Index(name = "idx_debt_document", columnList = "document_type, document_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Debt {

    public enum Status {
        UNPAID("Chưa thanh toán"),
        PARTIAL("Thanh toán một phần"),
        PAID("Đã thanh toán"),
        OVERDUE("Quá hạn");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }


    public enum PartyType { SUPPLIER, CUSTOMER }

    public enum DocumentType {
        WAREHOUSE_RECEIPT,   // phiếu nhập kho
        SALES_INVOICE,       // hóa đơn bán hàng
        GOOD_ISSUE,          // phiếu xuất kho (thêm mới)
        OTHER                // khác
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // === NEW: dùng chung cho NCC & KH ===
    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false)
    private PartyType partyType = PartyType.SUPPLIER; // default cho dữ liệu hiện tại

    // Nếu chưa có Customer entity thì để customerId dạng Long để compile an toàn
    @Column(name = "customer_id")
    private Long customerId; // sau này đổi sang ManyToOne Customer

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType = DocumentType.WAREHOUSE_RECEIPT;

    @Column(name = "document_id")
    private Long documentId;

    // === Các field cũ vẫn giữ nguyên ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_receipt_id")
    private WarehouseReceipt warehouseReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    private LocalDate invoiceDate;
    private LocalDate dueDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UNPAID;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        var now = java.time.LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = java.time.LocalDateTime.now();
    }
}
