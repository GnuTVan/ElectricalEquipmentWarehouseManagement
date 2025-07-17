package com.eewms.dto;

import com.eewms.entities.Product;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailsDTO {
    private Integer id;
    private String code;
    private String name;
    private BigDecimal originPrice;
    private BigDecimal listingPrice;
    private String description;
    private Product.ProductStatus status;
    private Integer quantity;

    private SettingDTO unit;
    private SettingDTO category;
    private SettingDTO brand;

    private List<ImageDTO> images;
}
