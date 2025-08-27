package com.eewms.services;

import com.eewms.dto.ComboDTO;
import com.eewms.dto.ComboRequest;
import com.eewms.entities.Combo;
import com.eewms.entities.Product;
import com.eewms.repository.ComboRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.services.impl.ComboServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComboServiceImplTest {

    @InjectMocks
    private ComboServiceImpl service;

    @Mock private ComboRepository comboRepository;
    @Mock private ProductRepository productRepository;

    private Product p1, p2;

    @BeforeEach
    void init() {
        p1 = new Product(); p1.setId(1); p1.setName("A");
        p2 = new Product(); p2.setId(2); p2.setName("B");
    }

    private ComboRequest req(List<ComboRequest.Item> items) {
        ComboRequest r = new ComboRequest();
        r.setName("  Combo   Test  ");
        r.setCode(null);
        r.setDetails(items);
        r.setStatus(Combo.ComboStatus.ACTIVE);
        return r;
    }

    @Test
    void create_NewNameNewCode_Ok_NormalizesAndSaves() {
        ComboRequest.Item i1 = new ComboRequest.Item(); i1.setProductId(1); i1.setQuantity(2);
        ComboRequest.Item i2 = new ComboRequest.Item(); i2.setProductId(2); i2.setQuantity(3);
        ComboRequest r = req(List.of(i1, i2));

        when(comboRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(comboRepository.existsByNameIgnoreCase(eq("Combo Test"))).thenReturn(false);
        when(productRepository.findAllById(List.of(1,2))).thenReturn(List.of(p1,p2));
        when(comboRepository.save(any(Combo.class))).thenAnswer(inv -> inv.getArgument(0));

        ComboDTO dto = service.create(r);

        assertEquals("Combo Test", dto.getName(), "Tên phải được normalize");
        assertNotNull(dto.getCode(), "Phải sinh code tự động khi bỏ trống");
    }


    @Test
    void create_DuplicateName_Throws() {
        ComboRequest.Item i1 = new ComboRequest.Item(); i1.setProductId(1); i1.setQuantity(1);
        ComboRequest r = req(List.of(i1));

        when(comboRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(comboRepository.existsByNameIgnoreCase(eq("Combo Test"))).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(r));
        assertTrue(ex.getMessage().contains("Tên combo đã tồn tại"));
    }

    @Test
    void create_InvalidQty_Throws() {
        ComboRequest.Item i1 = new ComboRequest.Item(); i1.setProductId(1); i1.setQuantity(0); // invalid
        ComboRequest r = req(List.of(i1));

        when(comboRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(comboRepository.existsByNameIgnoreCase(eq("Combo Test"))).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(r));
        assertTrue(ex.getMessage().contains("Số lượng phải từ 1"));
    }

    @Test
    void update_ChangeNameToExisting_Throws() {
        Combo existing = new Combo();
        existing.setId(5L);
        existing.setCode("CB001");
        existing.setName("Old");

        when(comboRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(comboRepository.existsByNameIgnoreCaseAndIdNot("New Name", 5L)).thenReturn(true);

        ComboRequest r = new ComboRequest();
        r.setName(" New Name ");
        r.setCode("CB001");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.update(5L, r));
        assertTrue(ex.getMessage().contains("Tên combo đã tồn tại"));
    }
}