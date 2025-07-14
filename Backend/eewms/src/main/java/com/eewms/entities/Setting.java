package com.eewms.entities;

import com.eewms.constant.SettingType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "setting")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Setting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "name")
    private String name;
    /**
     * Hibernate sẽ tự ALTER TABLE type_id sang VARCHAR(20)
     * và lưu ENUM.name() xuống DB
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_id", nullable = false, columnDefinition = "VARCHAR(20)")
    private SettingType type;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "status")
    private String status;
}
