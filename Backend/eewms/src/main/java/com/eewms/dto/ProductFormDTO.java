package com.eewms.dto;

import com.eewms.entities.Product;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
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

    @NotBlank(message = "Mã sản phẩm không được để trống")
    @Size(max = 50, message = "Mã sản phẩm tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 100, message = "Tên sản phẩm tối đa 100 ký tự")
    private String name;

    @NotNull(message = "Giá gốc không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá gốc phải >= 0")
    private BigDecimal originPrice;

    @NotNull(message = "Giá niêm yết không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá niêm yết phải >= 0")
    private BigDecimal listingPrice;

    @Size(max = 250, message = "Mô tả tối đa 250 ký tự")
    private String description;

    @NotNull(message = "Trạng thái không được để trống")
    private Product.ProductStatus status;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 0, message = "Số lượng phải >= 0")
    private Integer quantity;

    @NotNull(message = "Đơn vị không được để trống")
    private Integer unitId;

    @NotNull(message = "Danh mục không được để trống")
    private Integer categoryId;

    @NotNull(message = "Thương hiệu không được để trống")
    private Integer brandId;

    // Ảnh là optional
    @JsonIgnore
    private List<String> uploadedImageUrls;


    // id các ncc cho create/edit
    @Size(min = 1, message = "Vui lòng chọn ít nhất một nhà cung cấp")
    private List<Long> supplierIds;

    public void setCode(String code) {
        if (code == null) {
            this.code = null;
        } else {
            //trim -> remove all whitespaces -> UPPERCASE
            this.code = code.trim().replaceAll("\\s+", "").toUpperCase();
        }
    }
}
