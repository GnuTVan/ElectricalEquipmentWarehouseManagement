package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    private String address;

    private String taxCode;

    private String bankName;

    private String phone;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Customer.CustomerStatus status = Customer.CustomerStatus.ACTIVE; // mặc định là hoạt động

    public enum CustomerStatus {
        ACTIVE,     // Đang hoạt động
        INACTIVE    // Ngưng hoạt động
    }
}