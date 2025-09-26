package com.eewms.services;

import com.eewms.dto.SaleOrderDetailDTO;
import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.*;
import com.eewms.repository.*;
import com.eewms.services.impl.SaleOrderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleOrderServiceImplTest {

    @Mock private SaleOrderDetailRepository saleOrderDetailRepository;
    @Mock private ProductRepository productRepo;
    @Mock private SaleOrderRepository orderRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private UserRepository userRepo;
    @Mock private GoodIssueNoteRepository goodIssueRepository;
    @Mock private ComboRepository comboRepository;
    @Mock private SaleOrderComboRepository saleOrderComboRepository;

    @InjectMocks
    private SaleOrderServiceImpl service;

    @Test
    @DisplayName("createOrder: 1 manual detail, status mặc định PENDING, tính đúng tổng tiền")
    void createOrder_success_manualDetail() {
        Customer customer = new Customer();
        customer.setId(1L); // Integer
        when(customerRepo.findById(1L)).thenReturn(Optional.of(customer));

        User creator = new User();
        creator.setId(2L); // Integer
        creator.setUsername("admin");
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(creator));

        Product p1 = new Product();
        p1.setId(10);
        p1.setListingPrice(new BigDecimal("100000"));
        when(productRepo.getReferenceById(10)).thenReturn(p1);

        ArgumentCaptor<SaleOrder> orderCap = ArgumentCaptor.forClass(SaleOrder.class);
        when(orderRepo.save(any(SaleOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        SaleOrderDetailDTO d1 = new SaleOrderDetailDTO();
        d1.setProductId(10);
        d1.setOrderedQuantity(2);
        d1.setPrice(new BigDecimal("120000"));

        SaleOrderRequestDTO req = new SaleOrderRequestDTO();
        req.setCustomerId(1L);  // Integer
        req.setDetails(List.of(d1));
        req.setComboIds(Collections.emptyList());

        SaleOrderResponseDTO resp = service.createOrder(req, "admin");

        verify(customerRepo).findById(1L);
        verify(userRepo).findByUsername("admin");
        verify(productRepo).getReferenceById(10);
        verify(orderRepo, atLeastOnce()).save(orderCap.capture());

        SaleOrder saved = orderCap.getValue();
        assertThat(saved.getCustomer()).isSameAs(customer);
        assertThat(saved.getStatus()).isEqualTo(SaleOrder.SaleOrderStatus.PENDING);
        assertThat(saved.getDetails()).hasSize(1);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("240000"));

        assertNotNull(resp);
    }

    @Test
    @DisplayName("createOrder: Customer không tồn tại")
    void createOrder_customerNotFound() {
        when(customerRepo.findById(1L)).thenReturn(Optional.empty());

        SaleOrderRequestDTO req = new SaleOrderRequestDTO();
        req.setCustomerId(1L);
        req.setDetails(Collections.emptyList());
        req.setComboIds(Collections.emptyList());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createOrder(req, "admin"));
        assertTrue(ex.getMessage().toLowerCase().contains("customer not found"));
    }

    @Test
    @DisplayName("createOrder: detail + combo đều trống")
    void createOrder_emptyDetailsAndCombos() {
        when(customerRepo.findById(1L)).thenReturn(Optional.of(new Customer()));
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(new User()));

        SaleOrderRequestDTO req = new SaleOrderRequestDTO();
        req.setCustomerId(1L);
        req.setDetails(Collections.emptyList());
        req.setComboIds(Collections.emptyList());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createOrder(req, "admin"));
        assertTrue(ex.getMessage().contains("Chi tiết đơn hàng trống"));
    }

    @Test
    @DisplayName("updateOrderStatus: PENDING → DELIVERIED hợp lệ")
    void updateOrderStatus_pendingToDelivered_ok() {
        SaleOrder so = new SaleOrder();
        so.setStatus(SaleOrder.SaleOrderStatus.PENDING);
        when(orderRepo.findById(3)).thenReturn(Optional.of(so));

        service.updateOrderStatus(3, SaleOrder.SaleOrderStatus.DELIVERIED);

        assertThat(so.getStatus()).isEqualTo(SaleOrder.SaleOrderStatus.DELIVERIED);
        verify(orderRepo).save(so);
    }

    @Test
    @DisplayName("updateOrderStatus: chuyển trái rule → RuntimeException")
    void updateOrderStatus_invalidTransition_throws() {
        SaleOrder so = new SaleOrder();
        so.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
        when(orderRepo.findById(4)).thenReturn(Optional.of(so));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateOrderStatus(4, SaleOrder.SaleOrderStatus.PENDING));
        assertTrue(ex.getMessage().contains("Không thể cập nhật"));
    }

    @Test
    @DisplayName("updateOrderItems: chỉ cho phép khi status = PENDING")
    void updateOrderItems_onlyPending() {
        SaleOrder so = new SaleOrder();
        so.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
        when(orderRepo.findById(12)).thenReturn(Optional.of(so));

        SaleOrderRequestDTO form = new SaleOrderRequestDTO();
        form.setComboCounts(new LinkedHashMap<>());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.updateOrderItems(12, form));
        assertTrue(ex.getMessage().toLowerCase().contains("only pending"));
    }

    @Test
    @DisplayName("searchWithFilters: ủy quyền đúng cho repository (tham số cuối là Pageable)")
    void searchWithFilters_delegatesRepo() {
        SaleOrder so = new SaleOrder();
        so.setStatus(SaleOrder.SaleOrderStatus.PENDING);

        Page<SaleOrder> page = new PageImpl<>(List.of(so), PageRequest.of(0, 10), 1);
        when(orderRepo.searchWithFilters(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<SaleOrder> res = service.searchWithFilters(
                "abc", SaleOrder.SaleOrderStatus.PENDING,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(),
                0, 10
        );

        assertThat(res.getTotalElements()).isEqualTo(1);
        verify(orderRepo).searchWithFilters(any(), any(), any(), any(), any(Pageable.class));
    }
}
