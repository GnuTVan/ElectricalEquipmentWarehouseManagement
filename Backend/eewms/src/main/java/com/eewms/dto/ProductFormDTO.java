package com.eewms.dto;

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
    private String status;
    private Integer quantity;
    private Integer unitId;
    private Integer categoryId;
    private Integer brandId;
    private List<MultipartFile> imageFiles;
}
