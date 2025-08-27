package com.eewms.services;

import com.eewms.repository.ProductRepository;
import com.eewms.services.ImageUploadService;
import com.eewms.services.impl.ProductServicesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServicesImplTest {

    @InjectMocks
    private ProductServicesImpl service;

    @Mock private ProductRepository productRepository;
    @Mock private ImageUploadService imageUploadService;

    // ---------------- HELPER ----------------

    private Object newProductDTO(
            Integer id,
            String code,
            String name,
            String originalPrice,   // Giá gốc
            String listedPrice,     // Giá niêm yết
            Integer quantity,       // Số lượng
            String unit,            // Đơn vị
            String brand,           // Thương hiệu
            String category         // Danh mục
    ) throws Exception {
        Class<?> dtoClz;
        try { dtoClz = Class.forName("com.eewms.dto.ProductDTO"); }
        catch (ClassNotFoundException e) { dtoClz = Class.forName("com.eewms.dto.product.ProductDTO"); }

        Object dto = dtoClz.getDeclaredConstructor().newInstance();

        // id
        safeSet(dto, new String[]{"setId"}, (id == null ? null : id.longValue()),
                new Class[]{Long.class, long.class});

        // code
        safeSet(dto, new String[]{"setCode","setProductCode","setSku"}, code, String.class);

        // name
        safeSet(dto, new String[]{"setName","setProductName","setTitle"}, name, String.class);

        // original/base price
        if (originalPrice != null) {
            BigDecimal op = new BigDecimal(originalPrice);
            safeSet(dto,
                    new String[]{"setOriginalPrice","setOriginPrice","setBasePrice","setCostPrice"},
                    op, new Class[]{BigDecimal.class, Double.class, double.class});
        }

        // listed/selling/unit price
        if (listedPrice != null) {
            BigDecimal lp = new BigDecimal(listedPrice);
            safeSet(dto,
                    new String[]{"setListedPrice","setSellingPrice","setPrice","setUnitPrice","setSlingPrice"},
                    lp, new Class[]{BigDecimal.class, Double.class, double.class});
        }

        // quantity
        if (quantity != null) {
            safeSet(dto,
                    new String[]{"setQuantity","setQty","setStock","setAmount"},
                    quantity, new Class[]{Integer.class, int.class, Long.class, long.class});
        }

        // unit
        safeSet(dto, new String[]{"setUnit","setMeasurementUnit","setUnitName"}, unit, String.class);

        // brand
        safeSet(dto, new String[]{"setBrand","setBrandName"}, brand, String.class);

        // category
        safeSet(dto, new String[]{"setCategory","setCategoryName"}, category, String.class);

        return dto;
    }

    private void safeSet(Object target, String[] methodNames, Object arg, Class<?>... types) {
        if (arg == null) return;
        for (String name : methodNames) {
            for (Class<?> t : types) {
                try {
                    Method m = target.getClass().getMethod(name, t);
                    m.invoke(target, coerce(arg, t));
                    return; // set được rồi thì thoát
                } catch (NoSuchMethodException ignore) {
                } catch (Exception ignore) {
                }
            }
        }
    }

    private Object coerce(Object v, Class<?> t) {
        if (v == null) return null;
        if (t.isInstance(v)) return v;
        if (t == int.class || t == Integer.class) return Integer.parseInt(v.toString());
        if (t == long.class || t == Long.class) return Long.parseLong(v.toString());
        if (t == double.class || t == Double.class) return Double.parseDouble(v.toString());
        if (t == BigDecimal.class) return new BigDecimal(v.toString());
        if (t == String.class) return v.toString();
        return v;
    }

    private Object callCreate(Object dto) throws Exception {
        Method m = null;
        for (Method candidate : ProductServicesImpl.class.getMethods()) {
            if (candidate.getName().equals("create") && candidate.getParameterCount() == 1) { m = candidate; break; }
        }
        assertNotNull(m, "Không tìm thấy method create(..) trên ProductServicesImpl");
        return m.invoke(service, dto);
    }

    // -------------- SETUP --------------

    @BeforeEach
    void setup() {
        when(imageUploadService.uploadImage(any())).thenReturn("https://cdn/img.png");
    }

    // -------------- TESTS --------------

    @Test
    void create_Happy_SavesAndReturns_NotNull() throws Exception {
        // DỮ LIỆU GIỐNG ẢNH (hàng Confirm hợp lệ)
        Object dto = newProductDTO(
                null,
                "QD",                 // Mã sản phẩm
                "quat dien cay",      // Tên
                "100000",             // Giá gốc
                "110000",             // Giá niêm yết
                50,                   // Số lượng
                "cái",                // Đơn vị
                "PANASONIC",
                "bóng"
        );

        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Object result = callCreate(dto);

        assertNotNull(result);
        verify(productRepository, atLeastOnce()).save(any());
    }

    @Test
    void create_DuplicateCode_Throws_NoSave() throws Exception {
        // Trùng với sheet: mã "QD" đã tồn tại
        when(productRepository.existsByCode("QD")).thenReturn(true);

        Object dto = newProductDTO(
                null, "QD", "quat dien cay",
                "100000", "110000", 50, "cái", "PANASONIC", "bóng"
        );

        Exception ex = assertThrows(Exception.class, () -> callCreate(dto));
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        assertTrue(msg.contains("mã") || msg.contains("code") || msg.contains("đã tồn tại"));
        verify(productRepository, never()).save(any());
    }
}
