package com.eewms.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 50)
    @NotBlank(message = "Mã sản phẩm không được để trống")
    @Size(max = 50, message = "Mã sản phẩm tối đa 50 ký tự")
    private String code;

    @Column(nullable = false, length = 100)
    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 100, message = "Tên sản phẩm tối đa 100 ký tự")
    private String name;

    @Column(name = "origin_price", nullable = false)
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá gốc phải >= 0")
    @NotNull(message = "Giá gốc không được để trống")
    private BigDecimal originPrice;

    @Column(name = "listing_price", nullable = false)
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá niêm yết phải >= 0")
    @NotNull(message = "Giá niêm yết không được để trống")
    private BigDecimal listingPrice;

    @Column(columnDefinition = "TEXT")
    @Size(max = 250, message = "Mô tả tối đa 250 ký tự")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE; // mặc định là hoạt động

    public enum ProductStatus {
        ACTIVE,     // Đang hoạt động
        INACTIVE    // Ngưng hoạt động
    }


    @Column(nullable = false)
    @Min(value = 0, message = "Số lượng phải >= 0")
    @NotNull(message = "Số lượng không được để trống")
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    @NotNull(message = "Đơn vị không được để trống")
    private Setting unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @NotNull(message = "Danh mục không được để trống")
    private Setting category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    @NotNull(message = "Thương hiệu không được để trống")
    private Setting brand;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Image> images;

    @ManyToMany
    @JoinTable(
            name = "product_suppliers",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "supplier_id")
    )
    private Set<Supplier> suppliers = new HashSet<>();

    @Transient
    public List<Long> getSupplierIds() {
        return suppliers == null
                ? List.of()
                : suppliers.stream().map(Supplier::getId).collect(Collectors.toList());
    }

    @Transient
    public List<String> getSupplierNames() {
        return suppliers == null
                ? List.of()
                : suppliers.stream().map(Supplier::getName).collect(Collectors.toList());
    }

}

