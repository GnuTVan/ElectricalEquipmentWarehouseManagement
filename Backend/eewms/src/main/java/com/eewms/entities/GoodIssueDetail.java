package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "good_issue_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodIssueDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ginDetailsId;

    @ManyToOne
    @JoinColumn(name = "gin_id", nullable = false)
    private GoodIssueNote goodIssueNote;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    @Column(nullable = false)
    private Integer quantity; // ✅ Đếm rời

    @Column(name = "price", nullable = false)
    private BigDecimal price;
}
