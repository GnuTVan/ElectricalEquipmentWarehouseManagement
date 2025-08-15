package com.eewms.services;

import com.eewms.entities.GoodIssueNote;
import com.eewms.entities.Product;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrderDetail;
import com.eewms.entities.User;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.impl.GoodIssueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoodIssueServiceImplTest {

    @InjectMocks
    private GoodIssueServiceImpl service;

    @Mock private GoodIssueNoteRepository goodIssueNoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;

    private User admin;
    private Product p1, p2;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setUsername("admin");

        p1 = new Product(); p1.setId(1); p1.setName("A"); p1.setQuantity(10);
        p2 = new Product(); p2.setId(2); p2.setName("B"); p2.setQuantity(5);
    }

    @Test
    void createFromOrder_StockEnough_DeductsAndSaves() {
        // given
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(goodIssueNoteRepository.count()).thenReturn(0L); // để sinh mã GIN nếu service sử dụng count
        when(goodIssueNoteRepository.save(any(GoodIssueNote.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SaleOrderDetail d1 = new SaleOrderDetail();
        d1.setProduct(p1);
        d1.setOrderedQuantity(3);

        SaleOrderDetail d2 = new SaleOrderDetail();
        d2.setProduct(p2);
        d2.setOrderedQuantity(5);

        SaleOrder order = new SaleOrder();
        order.setSoCode("SO001");
        order.setDetails(List.of(d1, d2));

        // when
        GoodIssueNote note = service.createFromOrder(order, "admin");

        // then: trừ tồn
        assertEquals(7, p1.getQuantity());
        assertEquals(0, p2.getQuantity());
        assertNotNull(note.getGinCode(), "Phải sinh mã GIN");
        assertSame(admin, note.getCreatedBy(), "Người tạo phải là admin");

        // verify save
        verify(goodIssueNoteRepository, atLeastOnce()).save(any(GoodIssueNote.class));
        verify(productRepository, atLeast(2)).save(any(Product.class));
    }

    @Test
    void createFromOrder_InsufficientStock_ThrowsAndNotSave() {
        // given
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        Product low = new Product();
        low.setId(3);
        low.setName("LOW");
        low.setQuantity(1);

        SaleOrderDetail d = new SaleOrderDetail();
        d.setProduct(low);
        d.setOrderedQuantity(3);

        SaleOrder order = new SaleOrder();
        order.setSoCode("SO-LOW");
        order.setDetails(List.of(d));

        // then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createFromOrder(order, "admin")); // InventoryException cũng pass vì là RuntimeException
        assertTrue(ex.getMessage().toLowerCase().contains("không đủ")
                        || ex.getMessage().toLowerCase().contains("insufficient"),
                "Thông báo phải thể hiện thiếu hàng");

        verify(goodIssueNoteRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }
}
