package com.eewms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProductLiteDTO {
    //Dùng để preload danh mục sản phẩm nhẹ (id, code, name, unit name) cho chuyển kho
    private Integer id;
    private String code;
    private String name;
    private String unitName;
}
