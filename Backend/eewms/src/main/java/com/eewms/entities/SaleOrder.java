package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sale_orders")
public class SaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "so_id")
    private Integer soId;

    @Column(name = "so_code", unique = true, length = 10, updatable = false, nullable = false)
    private String soCode;

    @ManyToOne
    @JoinColumn(name = "customer", nullable = true)
    private Customer customer;

    @CreationTimestamp
    @Column(name = "order_date", nullable = false, updatable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SaleOrderStatus status = SaleOrderStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdByUser;

    @OneToMany(mappedBy = "sale_order", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<SaleOrderDetail> details;

    @Getter
    public enum SaleOrderStatus {
        // ===== Trạng thái dùng hiện tại =====
        PENDING("Chờ lấy hàng"),
        PARTLY_DELIVERED("Đã giao một phần"),
        DELIVERIED("Hoàn thành") ; // legacy

        private final String label;
        SaleOrderStatus(String label) { this.label = label; }
    }

    // Danh sách combo
    @OneToMany(mappedBy = "saleOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private java.util.List<SaleOrderCombo> combos = new java.util.ArrayList<>();

    // PAYOS
    public enum PaymentStatus {
        NONE_PAYMENT, // mới tạo đơn, chưa chọn luồng
        UNPAID,       // bán công nợ
        PENDING,      // chờ thanh toán qua PayOS
        PAID,         // đã thanh toán
        FAILED        // thanh toán thất bại/hủy
    }

    @Column(name = "payos_order_code", length = 100)
    private String payOsOrderCode;

    // Mặc định là PENDING, với đơn thanh toán luôn thì override từ khâu khởi tạo đơn hàng
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.NONE_PAYMENT;

    @PrePersist
    public void prePersist() {
        if (paymentStatus == null) paymentStatus = PaymentStatus.NONE_PAYMENT;
    }

    // Ghi chú thanh toán, vd: "Thanh toan don hang ORD-XXXXX, so tien xxxx vnd"
    @Column(name = "payment_note", length = 255)
    private String paymentNote;

    // ======= Convenience getter cho công nợ =======
    public Long getId() {
        return (soId != null) ? soId.longValue() : null;
    }
}
