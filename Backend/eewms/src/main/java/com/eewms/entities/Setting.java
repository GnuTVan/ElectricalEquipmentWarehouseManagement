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

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_id", nullable = false, columnDefinition = "VARCHAR(20)")
    private SettingType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private SettingStatus status = SettingStatus.ACTIVE;

    public enum SettingStatus {
        ACTIVE,
        INACTIVE
    }


}


