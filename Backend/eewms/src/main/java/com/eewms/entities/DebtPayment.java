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
public class DebtPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "debt_transaction_id")
    private DebtTransaction debtTransaction;

    private LocalDateTime paymentDate;

    private BigDecimal amount;

    private String method;

    private String note;
}
