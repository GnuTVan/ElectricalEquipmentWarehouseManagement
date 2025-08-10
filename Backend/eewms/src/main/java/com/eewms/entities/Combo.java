package com.eewms.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(
        name = "combos",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_combo_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_combo_name", columnNames = "name")
        }
)
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

    @Column(nullable = false, length = 255)   // <-- quan trọng: not null
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    private Combo.ComboStatus status = Combo.ComboStatus.ACTIVE;

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboDetail> details;

    public enum ComboStatus { ACTIVE, INACTIVE }
}
