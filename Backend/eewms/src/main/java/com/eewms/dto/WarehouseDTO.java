package com.eewms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter; import lombok.Setter;

@Getter @Setter
public class WarehouseDTO {
    private Integer id;

    @NotBlank(message = "Tên kho là bắt buộc")
    @Size(max = 100, message = "Tên kho tối đa 100 ký tự")
    private String name;

    @Size(max = 250, message = "Mô tả tối đa 250 ký tự")
    private String description;

    private Boolean status = true;
}
