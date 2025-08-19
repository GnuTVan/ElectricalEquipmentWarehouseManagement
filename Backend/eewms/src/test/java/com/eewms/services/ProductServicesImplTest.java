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

    private Object newProductDTO(Integer id, String code, String name, String price) throws Exception {
        Class<?> dtoClz;
        try { dtoClz = Class.forName("com.eewms.dto.ProductDTO"); }
        catch (ClassNotFoundException e) { dtoClz = Class.forName("com.eewms.dto.product.ProductDTO"); }
        Object dto = dtoClz.getDeclaredConstructor().newInstance();
        tryInvoke(dto, "setId", Integer.class, id);
        tryInvoke(dto, "setId", Long.class, (id == null ? null : id.longValue()));
        tryInvoke(dto, "setCode", String.class, code);
        tryInvoke(dto, "setName", String.class, name);
        if (price != null) {
            BigDecimal p = new BigDecimal(price);
            tryInvoke(dto, "setPrice", BigDecimal.class, p);
            tryInvoke(dto, "setSellingPrice", BigDecimal.class, p);
            tryInvoke(dto, "setUnitPrice", BigDecimal.class, p);
        }
        return dto;
    }
    private static void tryInvoke(Object target, String method, Class<?> type, Object arg) {
        try { target.getClass().getMethod(method, type).invoke(target, arg); } catch (Exception ignore) {}
    }
    private Object callCreate(Object dto) throws Exception {
        Method m = null;
        for (Method candidate : ProductServicesImpl.class.getMethods()) {
            if (candidate.getName().equals("create") && candidate.getParameterCount() == 1) { m = candidate; break; }
        }
        assertNotNull(m, "Không tìm thấy method create(..) trên ProductServicesImpl");
        return m.invoke(service, dto);
    }
    // --------------------------------

    @BeforeEach
    void setup() {
        when(imageUploadService.uploadImage(any())).thenReturn("https://cdn/img.png");
    }

    @Test
    void create_Happy_SavesAndReturns_NotNull() throws Exception {
        Object dto = newProductDTO(null, null, "Product A", "12.50");
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Object result = callCreate(dto);

        assertNotNull(result);
        verify(productRepository, atLeastOnce()).save(any());
    }

    @Test
    void create_DuplicateCode_Throws_NoSave() throws Exception {
        // 👇 ĐỔI TÊN METHOD NÀY CHO KHỚP VỚI REPOSITORY CỦA BẠN
        when(productRepository.existsByCode("P001")).thenReturn(true);

        Object dto = newProductDTO(null, "P001", "Anything", "1.00");

        Exception ex = assertThrows(Exception.class, () -> callCreate(dto));
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        assertTrue(msg.contains("mã") || msg.contains("code") || msg.contains("đã tồn tại"));
        verify(productRepository, never()).save(any());
    }
}

