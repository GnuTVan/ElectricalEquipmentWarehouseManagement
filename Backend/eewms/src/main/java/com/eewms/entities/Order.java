package com.eewms.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "po_id")
    private Integer poId;

    @Column(name = "po_code", unique = true, length = 10, updatable = false, nullable = false)
    private String poCode;

    @ManyToOne
    @JoinColumn(name = "customer", nullable = true)
    private Customer customer;

    @CreationTimestamp
    @Column(name = "order_date", nullable = false, updatable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING; // Mặc định là "Chờ nhận"

    @Column(columnDefinition = "TEXT")
    private String description; // ✅ Thêm ghi chú đơn hàng

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    public enum OrderStatus {
        PENDING("Chờ nhận"),
        COMPLETED("Hoàn thành"),
        CANCELLED("Hủy");

        private final String label;

        OrderStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdByUser;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<OrderDetail> details;

}