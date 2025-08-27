package com.eewms.services;

import com.eewms.dto.SupplierDTO;
import com.eewms.entities.Supplier;
import com.eewms.exception.InventoryException;
import com.eewms.repository.SupplierRepository;
import com.eewms.services.impl.SupplierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @InjectMocks
    private SupplierServiceImpl service;

    @Mock
    private SupplierRepository supplierRepository;

    private SupplierDTO dto;

    @BeforeEach
    void init() {
        dto = new SupplierDTO();
        dto.setId(1L);
        dto.setName("Cong Ty A");
        dto.setTaxCode("  T123 ");
        dto.setBankName(" Vietcombank ");
        dto.setBankAccount(" 0123456789 ");
        dto.setContactName("  Nguyen  A ");
        dto.setContactMobile(" 090000001 ");
        dto.setAddress("  12  abc ");
        dto.setStatus(null); // null -> service sẽ set TRUE
        dto.setDescription("  note ");
    }

    @Test
    void create_UniqueFields_Normalizes_AndSaves() {
        when(supplierRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(supplierRepository.existsByTaxCode(anyString())).thenReturn(false);
        when(supplierRepository.existsByBankAccount(anyString())).thenReturn(false);
        when(supplierRepository.existsByContactMobile(anyString())).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(dto);

        ArgumentCaptor<Supplier> cap = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(cap.capture());
        Supplier saved = cap.getValue();

        assertEquals("Cong Ty A", saved.getName());
        assertEquals("T123", saved.getTaxCode());
        assertEquals("Vietcombank", saved.getBankName());
        assertEquals("0123456789", saved.getBankAccount());
        assertEquals("Nguyen  A", saved.getContactName());
        assertEquals("090000001", saved.getContactMobile());
        assertEquals("12  abc", saved.getAddress());
        assertTrue(Boolean.TRUE.equals(saved.getStatus()));
        assertEquals("note", saved.getDescription());
    }

    @Test
    void create_DuplicateName_Throws_NoSave() {
        when(supplierRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);
        InventoryException ex = assertThrows(InventoryException.class, () -> service.create(dto));
        assertTrue(ex.getMessage().toLowerCase().contains("đã tồn tại"));
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void update_Valid_NoDuplicates_UpdatesAndSaves() {
        Supplier existing = Supplier.builder()
                .id(1L).name("Old").taxCode("OLD").bankName("B").bankAccount("111")
                .contactName("C").contactMobile("0900").address("addr").status(true).description("d")
                .build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supplierRepository.existsByNameIgnoreCaseAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(supplierRepository.existsByTaxCodeAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(supplierRepository.existsByBankAccountAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(supplierRepository.existsByContactMobileAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        dto.setName(" Cong Ty A (new) ");
        dto.setTaxCode("  T999 ");
        dto.setBankAccount(" 999 ");
        dto.setContactMobile(" 0909 ");
        dto.setStatus(null); // giữ nguyên status hiện tại

        service.update(dto);

        assertEquals("Cong Ty A (new)", existing.getName());
        assertEquals("T999", existing.getTaxCode());
        assertEquals("999", existing.getBankAccount());
        assertEquals("0909", existing.getContactMobile());
        assertTrue(existing.getStatus());
        verify(supplierRepository).save(existing);
    }

    @Test
    void update_DuplicateBankAccount_Throws() {
        SupplierDTO dto = new SupplierDTO();
        dto.setId(1L);
        dto.setName("Cong Ty A");
        dto.setBankAccount("Vietcombank");

        Supplier existing = Supplier.builder()
                .id(1L)
                .name("Cong Ty A")
                .build();

        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(existing));
        when(supplierRepository.existsByBankAccountAndIdNot(eq("Vietcombank"), eq(1L)))
                .thenReturn(true);

        InventoryException ex = assertThrows(InventoryException.class,
                () -> service.update(dto));

        assertTrue(ex.getMessage().contains("Số tài khoản"));
        verify(supplierRepository, never()).save(any());
    }


    @Test
    void toggleStatus_Toggles_AndSaves() {
        Supplier s = new Supplier(); s.setId(5L); s.setStatus(true);
        when(supplierRepository.findById(5L)).thenReturn(Optional.of(s));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.toggleStatus(5L);

        assertFalse(s.getStatus());
        verify(supplierRepository).save(s);
    }

    @Test
    void findById_ReturnsDTO() {
        Supplier s = Supplier.builder().id(7L).name("ABC").taxCode("T1").status(true).build();
        when(supplierRepository.findById(7L)).thenReturn(Optional.of(s));

        SupplierDTO got = service.findById(7L);
        assertNotNull(got);
        assertEquals(7L, got.getId());
        assertEquals("ABC", got.getName());
    }

    @Test
    void searchSuppliers_DelegatesToRepo() {
        Supplier s = Supplier.builder().id(1L).name("ABC").taxCode("T1").status(true).build();
        Page<Supplier> page = new PageImpl<>(List.of(s));
        when(supplierRepository.findByNameContainingIgnoreCaseOrTaxCodeContainingIgnoreCase(
                eq("a"), eq("a"), any(Pageable.class))).thenReturn(page);

        Page<SupplierDTO> res = service.searchSuppliers(0, "a");
        assertEquals(1, res.getTotalElements());
        assertEquals("ABC", res.getContent().get(0).getName());
    }
}

