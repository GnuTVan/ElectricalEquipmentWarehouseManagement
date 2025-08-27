//package com.eewms.services;
//
//import com.eewms.dto.SaleOrderDetailDTO;
//import com.eewms.dto.SaleOrderRequestDTO;
//import com.eewms.dto.SaleOrderResponseDTO;
//import com.eewms.entities.*;
//import com.eewms.repository.*;
//import com.eewms.services.impl.SaleOrderServiceImpl;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class SaleOrderServiceImplTest {
//
//    @InjectMocks
//    private SaleOrderServiceImpl service;
//
//    @Mock private SaleOrderRepository orderRepo;
//    @Mock private ProductRepository productRepo;
//    @Mock private CustomerRepository customerRepo;
//    @Mock private UserRepository userRepo;
//    @Mock private GoodIssueNoteRepository goodIssueRepository;
//    @Mock private ComboRepository comboRepository;
//
//    private Customer customer;
//    private User user;
//    private Product pEnough, pLack;
//    private Combo comboA, comboB;
//
//    @BeforeEach
//    void init() {
//        customer = new Customer();
//        // Nếu Customer có setId thì giữ lại, còn không thì bỏ hẳn dòng này:
//        // customer.setId(1L);
//
//        user = new User();
//        user.setUsername("admin"); // KHÔNG gọi setUserId (không cần)
//
//        pEnough = new Product(); pEnough.setId(10); pEnough.setName("P-OK");   pEnough.setQuantity(5);
//        pLack   = new Product(); pLack.setId(11);   pLack.setName("P-LACK"); pLack.setQuantity(1);
//
//        comboA = new Combo(); comboA.setName("Combo A");
//        comboB = new Combo(); comboB.setName("Combo B");
//    }
//
//
//    @Test
//    void createOrder_HasInsufficientStock_AppendsWarningAndComboAndSaves() {
//        // Given DTO with 2 lines (1 thiếu hàng)
//        SaleOrderRequestDTO req = SaleOrderRequestDTO.builder()
//                .customerId(1L)
//                .description("Giao trong ngày")
//                .details(List.of(
//                        SaleOrderDetailDTO.builder().productId(10).price(new BigDecimal("2.5")).orderedQuantity(3).build(),
//                        SaleOrderDetailDTO.builder().productId(11).price(new BigDecimal("4.0")).orderedQuantity(2).build() // thiếu
//                ))
//                .comboIds(List.of(1L, 2L))
//                .build();
//
//        when(customerRepo.findById(1L)).thenReturn(Optional.of(customer));
//        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
//        when(productRepo.findById(10)).thenReturn(Optional.of(pEnough));
//        when(productRepo.findById(11)).thenReturn(Optional.of(pLack));
//        when(orderRepo.count()).thenReturn(0L);
//        when(comboRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(comboA, comboB));
//        when(orderRepo.save(any(SaleOrder.class))).thenAnswer(inv -> inv.getArgument(0));
//
//        // When
//        SaleOrderResponseDTO res = service.createOrder(req, "admin");
//
//        // Then
//        assertNotNull(res.getOrderCode(), "Phải sinh mã đơn hàng");
//        assertTrue(res.getDescription().contains("thiếu hàng"), "Mô tả phải có cảnh báo thiếu hàng");
//        assertTrue(res.getDescription().contains("Đơn có combo: Combo A, Combo B"),
//                "Mô tả phải nối nhãn combo");
//        // Tổng tiền = 3*2.5 + 2*4.0 = 7.5 + 8 = 15.5
//        assertEquals(new BigDecimal("15.5"), res.getTotalAmount());
//        verify(orderRepo, atLeastOnce()).save(any(SaleOrder.class));
//    }
//
//    @Test
//    void getById_AlreadyExported_FlagsSet() {
//        SaleOrder order = new SaleOrder();
//        order.setSoId(99);
//        order.setStatus(SaleOrder.SaleOrderStatus.PENDING);
//        // 1 dòng chi tiết đủ hàng
//        Product p = new Product(); p.setQuantity(10);
//        SaleOrderDetail d = new SaleOrderDetail(); d.setProduct(p); d.setOrderedQuantity(2);
//        order.setDetails(List.of(d));
//
//        when(orderRepo.findById(99)).thenReturn(Optional.of(order));
//        when(goodIssueRepository.existsBySaleOrder_SoId(99)).thenReturn(true);
//
//        SaleOrderResponseDTO dto = service.getById(99);
//        assertTrue(dto.isAlreadyExported(), "Đã xuất kho phải là true");
//        assertFalse(dto.isHasInsufficientStock(), "Đã xuất kho thì không xét thiếu hàng");
//    }
//
//    @Test
//    void getById_NotExported_ButStillMissingStock_FlagTrue() {
//        SaleOrder order = new SaleOrder();
//        order.setSoId(100);
//        Product p = new Product(); p.setQuantity(1);
//        SaleOrderDetail d = new SaleOrderDetail(); d.setProduct(p); d.setOrderedQuantity(3);
//        order.setDetails(List.of(d));
//
//        when(orderRepo.findById(100)).thenReturn(Optional.of(order));
//        when(goodIssueRepository.existsBySaleOrder_SoId(100)).thenReturn(false);
//
//        SaleOrderResponseDTO dto = service.getById(100);
//        assertTrue(dto.isHasInsufficientStock(), "Chưa xuất kho và thiếu hàng -> true");
//    }
//
//    @Test
//    void updateOrderStatus_PendingToDeliveried_ThenDeliveriedToCompleted() {
//        SaleOrder order = new SaleOrder();
//        order.setSoId(1);
//        order.setStatus(SaleOrder.SaleOrderStatus.PENDING);
//        when(orderRepo.findById(1)).thenReturn(Optional.of(order));
//
//        // Pending -> Deliveried
//        service.updateOrderStatus(1, SaleOrder.SaleOrderStatus.DELIVERIED);
//        assertEquals(SaleOrder.SaleOrderStatus.DELIVERIED, order.getStatus());
//        verify(orderRepo, times(1)).save(order);
//
//        // Deliveried -> Completed
//        order.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
//        when(orderRepo.findById(1)).thenReturn(Optional.of(order));
//        service.updateOrderStatus(1, SaleOrder.SaleOrderStatus.COMPLETED);
//        assertEquals(SaleOrder.SaleOrderStatus.COMPLETED, order.getStatus());
//        verify(orderRepo, times(2)).save(order);
//    }
//
//    @Test
//    void updateOrderStatus_InvalidTransition_Throws() {
//        SaleOrder order = new SaleOrder();
//        order.setSoId(2);
//        order.setStatus(SaleOrder.SaleOrderStatus.PENDING);
//        when(orderRepo.findById(2)).thenReturn(Optional.of(order));
//
//        RuntimeException ex = assertThrows(RuntimeException.class,
//                () -> service.updateOrderStatus(2, SaleOrder.SaleOrderStatus.COMPLETED));
//        assertTrue(ex.getMessage().contains("Không thể cập nhật"));
//        verify(orderRepo, never()).save(any());
//    }
//}