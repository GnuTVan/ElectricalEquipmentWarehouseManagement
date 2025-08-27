package com.eewms.services;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.Mockito.lenient;
import com.eewms.dto.CustomerDTO;
import com.eewms.dto.CustomerMapper;
import com.eewms.entities.Customer;
import com.eewms.repository.CustomerRepository;
import com.eewms.services.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)

class CustomerServiceImplTest {

    @InjectMocks
    private CustomerServiceImpl service;

    @Mock private CustomerRepository repo;
    @Mock private CustomerMapper mapper;

    private CustomerDTO dto;      // DTO đầu vào
    private Customer entity;      // Entity ánh xạ

    @BeforeEach
    void setUp() {
        dto = new CustomerDTO();
        dto.setId(1L);
        dto.setFullName("Nguyenvanthinh");
        dto.setEmail("  A@example.com  ");
        dto.setPhone("0941258801");
        dto.setAddress("bắc ninh");
        dto.setTaxCode("  0101234567  ");
        dto.setBankName("  BIDV  ");
        dto.setStatus(null);

        entity = new Customer();
        entity.setId(1L);
        entity.setFullName("Nguyenvanthinh");
        entity.setEmail("A@example.com");
        entity.setPhone("0941258801");
        entity.setAddress("bắc ninh");
        entity.setTaxCode("0101234567");
        entity.setBankName("BIDV");
        entity.setStatus(Customer.CustomerStatus.ACTIVE);


        when(mapper.toEntity(any(CustomerDTO.class))).thenAnswer(inv -> {
            CustomerDTO d = inv.getArgument(0);
            Customer e = new Customer();
            e.setId(d.getId());
            e.setFullName(d.getFullName());
            e.setEmail(d.getEmail());
            e.setPhone(d.getPhone());
            e.setAddress(d.getAddress());
            e.setTaxCode(d.getTaxCode());
            e.setBankName(d.getBankName());
            e.setStatus(d.getStatus());
            return e;
        });


        when(mapper.toDTO(any(Customer.class))).thenAnswer(inv -> {
            Customer e = inv.getArgument(0);
            CustomerDTO d = new CustomerDTO();
            d.setId(e.getId());
            d.setFullName(e.getFullName());
            d.setEmail(e.getEmail());
            d.setPhone(e.getPhone());
            d.setAddress(e.getAddress());
            d.setTaxCode(e.getTaxCode());
            d.setBankName(e.getBankName());
            d.setStatus(e.getStatus());
            return d;
        });
    }

    @Test
    void create_UniqueEmailAndPhone_NormalizesAndSaves() {
        // không trùng
        when(repo.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(repo.existsByPhone(anyString())).thenReturn(false);
        when(repo.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerDTO res = service.create(dto);

        assertNotNull(res);
        assertEquals("Nguyenvanthinh", res.getFullName(), "fullName phải được title-case");
        assertEquals(Customer.CustomerStatus.ACTIVE, res.getStatus(), "status null -> ACTIVE");

        assertEquals("A@example.com", res.getEmail());
        assertEquals("0941258801", res.getPhone());
        assertEquals("bắc ninh", res.getAddress());

        verify(repo).save(any(Customer.class));
        verify(repo).existsByEmailIgnoreCase("A@example.com");
        verify(repo).existsByPhone("0941258801");
    }

    @Test
    void create_DuplicatePhone_Throws() {
        when(repo.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(repo.existsByPhone(anyString())).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(dto));
        assertTrue(ex.getMessage().contains("Số điện thoại đã tồn tại"));
        verify(repo, never()).save(any());
    }

    @Test
    void update_Valid_NoDuplicate_Saves() {
        // tìm thấy entity hiện tại
        when(repo.findById(1L)).thenReturn(Optional.of(new Customer()));
        // không trùng (loại trừ chính nó)
        when(repo.existsByEmailIgnoreCaseAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(repo.existsByPhoneAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(repo.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));


        dto.setFullName("   Nguyenvanthinh  (new) ");
        dto.setEmail("  b@Example.com ");
        dto.setPhone(" 0941258811 ");

        CustomerDTO res = service.update(dto);

        assertEquals("Nguyenvanthinh (new)", res.getFullName());
        assertEquals("b@Example.com", res.getEmail());
        assertEquals("0941258811", res.getPhone());
        verify(repo).save(any(Customer.class));
        verify(repo).existsByEmailIgnoreCaseAndIdNot("b@Example.com", 1L);
        verify(repo).existsByPhoneAndIdNot("0941258811", 1L);
    }

    @Test
    void update_EmailDuplicate_Throws() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Customer()));
        when(repo.existsByEmailIgnoreCaseAndIdNot(anyString(), eq(1L))).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.update(dto));
        assertTrue(ex.getMessage().contains("Email đã tồn tại"));
        verify(repo, never()).save(any());
    }

    @Test
    void getById_Found_ReturnsDTO() {
        when(repo.findById(1L)).thenReturn(Optional.of(entity));
        CustomerDTO res = service.getById(1L);
        assertEquals(1L, res.getId());
        assertEquals("Nguyenvanthinh", res.getFullName());
        verify(repo).findById(1L);
    }

    @Test
    void getById_NotFound_Throws() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.getById(999L));
        assertTrue(ex.getMessage().contains("Không tìm thấy khách hàng"));
    }

    @Test
    void delete_DelegatesToRepo() {
        doNothing().when(repo).deleteById(1L);
        service.delete(1L);
        verify(repo).deleteById(1L);
    }

    @Test
    void updateStatus_ChangesAndSaves() {
        Customer cur = new Customer();
        cur.setId(1L);
        cur.setStatus(Customer.CustomerStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(cur));
        when(repo.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(1L, Customer.CustomerStatus.INACTIVE);

        assertEquals(Customer.CustomerStatus.INACTIVE, cur.getStatus());
        verify(repo).save(cur);
    }

    @Test
    void searchByKeyword_MapsToDTO() {
        when(repo.searchByKeyword("an")).thenReturn(List.of(entity));
        List<CustomerDTO> res = service.searchByKeyword("an");
        assertEquals(1, res.size());
        assertEquals("Nguyenvanthinh", res.get(0).getFullName());
        verify(repo).searchByKeyword("an");
    }
}
