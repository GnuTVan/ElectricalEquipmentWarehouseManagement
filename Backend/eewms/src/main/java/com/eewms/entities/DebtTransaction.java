package com.eewms.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Type { RECEIVABLE, PAYABLE }
    public enum PartnerType { CUSTOMER, SUPPLIER }
    public enum Status { UNPAID, PARTIALLY_PAID, PAID }

    @Enumerated(EnumType.STRING)
    private Type type;

    @Enumerated(EnumType.STRING)
    private PartnerType partnerType;

    private Long partnerId; // ID của Customer hoặc Supplier

    private BigDecimal amount;

    private BigDecimal paidAmount;

    private BigDecimal remaining;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String note;

    private LocalDateTime createdDate;

    private LocalDateTime dueDate;
}
