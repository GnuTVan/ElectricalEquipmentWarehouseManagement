package com.eewms.dto;

import com.eewms.constant.SettingType;
import com.eewms.entities.Setting;
import static com.eewms.exception.RegexPattern.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingDTO {
    private Integer id;

    @NotBlank(message = "Tên không được để trống")
    @Size(max = 100, message = "Tên tối đa 100 ký tự")
    @Pattern(regexp = NAME, message = "Tên chỉ được chứa chữ cái, khoảng trắng và dấu nháy đơn (ví dụ: O'Connor)")
    private String name;

    private SettingType type; 

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    private Setting.SettingStatus status;
}