package com.eewms.dto;

import com.eewms.constant.SettingType;
import com.eewms.entities.Setting;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingDTO {
    private Integer id;
    private String name;
    private SettingType type;
    private String description;
    private Setting.SettingStatus status;
}

