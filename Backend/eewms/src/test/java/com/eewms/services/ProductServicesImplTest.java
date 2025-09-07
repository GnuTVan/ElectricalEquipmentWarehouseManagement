package com.eewms.services;

import com.eewms.dto.ProductFormDTO;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.entities.Image;
import com.eewms.entities.Product;
import com.eewms.entities.Setting;
import com.eewms.entities.Supplier;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ImagesRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.SettingRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.services.impl.ProductServicesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServicesImplTest {

    @Mock private ProductRepository productRepo;
    @Mock private SettingRepository settingRepo;
    @Mock private ImagesRepository imageRepo;
    @Mock private SupplierRepository supplierRepo;
    @Mock private ImageUploadService imageUploadService;

    @InjectMocks
    private ProductServicesImpl service;

    private ProductFormDTO baseDto;
    private Setting unit, category, brand;
    private Supplier supplier;

    private Product.ProductStatus anyStatus() {
        return Product.ProductStatus.values()[0];
    }

    @BeforeEach
    void setUp() {
        unit = Setting.builder().id(Integer.valueOf(1)).name("Unit").build();
        category = Setting.builder().id(2).name("Category").build();
        brand = Setting.builder().id(Integer.valueOf(3)).name("Brand").build();
        supplier = Supplier.builder().id(Long.valueOf(Integer.valueOf(10))).name("Supplier").build();

        baseDto = new ProductFormDTO();
        baseDto.setCode("P01");
        baseDto.setName("Quat dien");
        baseDto.setOriginPrice(new BigDecimal("100"));
        baseDto.setListingPrice(new BigDecimal("120"));
        baseDto.setStatus(anyStatus());              // enum, không phải boolean
        baseDto.setQuantity(10);
        baseDto.setUnitId(Integer.valueOf(1));       // Integer, không phải long
        baseDto.setCategoryId(Integer.valueOf(2));
        baseDto.setBrandId(Integer.valueOf(3));
        baseDto.setSupplierIds(List.of()); // List<Integer>
    }

    @Test
    @DisplayName("Thêm sản phẩm mới hợp lệ -> save thành công, trả về DTO")
    void createProduct_Success() {
        when(productRepo.existsByCode("P01")).thenReturn(false);
        when(settingRepo.findById(Integer.valueOf(1))).thenReturn(Optional.of(unit));
        when(settingRepo.findById(Integer.valueOf(2))).thenReturn(Optional.of(category));
        when(settingRepo.findById(Integer.valueOf(3))).thenReturn(Optional.of(brand));
        when(supplierRepo.findAllById(List.of())).thenReturn(List.of(supplier));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(Integer.valueOf(99));
            return p;
        });

        ProductDetailsDTO result = invokeSaveOrUpdate(null, baseDto);

        assertEquals("P01", result.getCode());
        assertEquals("Quat dien", result.getName());
        assertEquals(new BigDecimal("120"), result.getListingPrice());
        verify(productRepo).save(any());
    }

    @Test
    @DisplayName("Thêm sản phẩm trùng mã -> InventoryException")
    void createProduct_DuplicateCode_Throws() {
        when(productRepo.existsByCode("P01")).thenReturn(true);

        assertThrows(InventoryException.class,
                () -> invokeSaveOrUpdate(null, baseDto));
        verify(productRepo, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật với mã trùng (khác id) -> InventoryException")
    void updateProduct_DuplicateCodeOtherId_Throws() {
        Product other = new Product();
        other.setId(Integer.valueOf(200));
        other.setCode("P01");

        when(productRepo.findById(Integer.valueOf(100))).thenReturn(Optional.of(new Product()));
        when(productRepo.findByCode("P01")).thenReturn(Optional.of(other));

        assertThrows(InventoryException.class,
                () -> invokeSaveOrUpdate(Integer.valueOf(100), baseDto));
    }

    @Test
    @DisplayName("Thiếu unit/category/brand -> InventoryException")
    void missingSetting_Throws() {
        when(productRepo.existsByCode("P01")).thenReturn(false);
        when(settingRepo.findById(Integer.valueOf(1))).thenReturn(Optional.empty()); // thiếu unit

        assertThrows(InventoryException.class,
                () -> invokeSaveOrUpdate(null, baseDto));
    }

    @Test
    @DisplayName("Supplier không tồn tại -> InventoryException")
    void missingSupplier_Throws() {
        when(productRepo.existsByCode("P01")).thenReturn(false);
        // Cho mọi id setting đều có để đi qua tới supplier
        when(settingRepo.findById(anyInt())).thenReturn(Optional.of(unit));
        when(supplierRepo.findAllById(List.of())).thenReturn(List.of()); // empty list

        assertThrows(InventoryException.class,
                () -> invokeSaveOrUpdate(null, baseDto));
    }

    @Test
    @DisplayName("Cập nhật có ảnh mới -> xóa ảnh cũ (gọi deleteImageByUrl) và lưu ảnh mới")
    void updateProduct_WithNewImages_DeleteOldImages() {
        Product existing = new Product();
        existing.setId(Integer.valueOf(50));

        when(productRepo.findById(Integer.valueOf(50))).thenReturn(Optional.of(existing));
        when(settingRepo.findById(anyInt())).thenReturn(Optional.of(unit));
        when(supplierRepo.findAllById(any())).thenReturn(List.of(supplier));
        when(productRepo.save(any(Product.class))).thenReturn(existing);

        // Ảnh cũ của sản phẩm
        Image old = Image.builder().id(Integer.valueOf(1)).imageUrl("old.png").build();
        when(imageRepo.findByProductId(Integer.valueOf(50))).thenReturn(List.of(old));

        // Ảnh mới người dùng up (đuôi |thumbnail theo đúng xử lý trong service)
        baseDto.setUploadedImageUrls(List.of("new.png|thumbnail"));

        ProductDetailsDTO dto = invokeSaveOrUpdate(Integer.valueOf(50), baseDto);

        assertEquals(Integer.valueOf(50), dto.getId());
        verify(imageUploadService).deleteImageByUrl("old.png");
        verify(imageRepo).deleteAll(any());
        verify(imageRepo).saveAll(any());
    }

    // ===== Helper: gọi method private saveOrUpdate(...) và nhận về ProductDetailsDTO
    private ProductDetailsDTO invokeSaveOrUpdate(Integer id, ProductFormDTO dto) {
        try {
            var m = ProductServicesImpl.class.getDeclaredMethod(
                    "saveOrUpdate", Integer.class, ProductFormDTO.class);
            m.setAccessible(true);
            Object ret = m.invoke(service, id, dto);
            return (ProductDetailsDTO) ret;
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }
}
