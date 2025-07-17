package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 250)
    private String name;

    @Column(length = 50, unique = true)
    private String taxCode;

    @Column(length = 100)
    private String bankName;

    @Column(length = 50)
    private String bankAccount;

    @Column(length = 100)
    private String contactName;

    @Column(length = 20)
    private String contactMobile;

    @Column(length = 250)
    private String address;

    @Column(nullable = false)
    private Boolean status = true;


    @Column(length = 250)
    private String description;


}
