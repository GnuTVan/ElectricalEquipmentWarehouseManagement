package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "combos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Combo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private Combo.ComboStatus status = Combo.ComboStatus.ACTIVE;

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboDetail> details;

    public enum ComboStatus {
        ACTIVE, INACTIVE
    }
}
