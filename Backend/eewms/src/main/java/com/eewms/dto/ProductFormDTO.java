package com.eewms.dto;

import com.eewms.entities.Product;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFormDTO {
    private Integer id;
    private String code;
    private String name;
    private BigDecimal originPrice;
    private BigDecimal listingPrice;
    private String description;
    private Product.ProductStatus status;
    private Integer quantity;
    private Integer unitId;
    private Integer categoryId;
    private Integer brandId;
//    private List<String> images;
    @JsonIgnore
    private List<String> uploadedImageUrls;
}
