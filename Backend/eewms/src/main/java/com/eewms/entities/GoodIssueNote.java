package com.eewms.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "good_issue_note")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodIssueNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ginId;

    @Column(name = "gin_code", length = 50, unique = true, nullable = false)
    private String ginCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime issueDate;

    @ManyToOne
    @JoinColumn(name = "customer")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "so_id", nullable = false)
    private SaleOrder saleOrder;


    @OneToMany(mappedBy = "goodIssueNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodIssueDetail> details;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;
}

