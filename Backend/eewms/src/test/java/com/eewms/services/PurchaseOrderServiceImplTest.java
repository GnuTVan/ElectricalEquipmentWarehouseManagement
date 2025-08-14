package com.eewms.services;


import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderItemDTO;
import com.eewms.entities.Product;
import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.PurchaseOrderItem;
import com.eewms.entities.Supplier;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.services.ImageUploadService;
import com.eewms.services.impl.PurchaseOrderServiceImpl;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @InjectMocks
    private PurchaseOrderServiceImpl service;

    @Mock private PurchaseOrderRepository orderRepo;
    @Mock private SupplierRepository supplierRepo;
    @Mock private ProductRepository productRepo;
    @Mock private ImageUploadService uploadService;

    private Supplier supplier;
    private Product p1, p2;

    @BeforeEach
    void setUp() {
        supplier = Supplier.builder().id(1L).name("ACME").build();

        p1 = new Product();
        p1.setId(10);
        p1.setName("P1");
        p1.setQuantity(0);
        p1.setSuppliers(Set.of(supplier)); // thuộc đúng NCC

        p2 = new Product();
        p2.setId(11);
        p2.setName("P2");
        p2.setQuantity(0);
        p2.setSuppliers(Set.of(supplier)); // thuộc đúng NCC
    }

    private PurchaseOrderDTO buildDtoWithItems(boolean withAttachment) {
        PurchaseOrderItemDTO i1 = PurchaseOrderItemDTO.builder()
                .productId(10)
                .contractQuantity(3)
                .actualQuantity(null)
                .price(new BigDecimal("2.50"))
                .build();
        PurchaseOrderItemDTO i2 = PurchaseOrderItemDTO.builder()
                .productId(11)
                .contractQuantity(5)
                .actualQuantity(null)
                .price(new BigDecimal("1.20"))
                .build();

        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        dto.setSupplierId(1L);
        dto.setCreatedByName("admin");
        dto.setItems(List.of(i1, i2));
        if (withAttachment) {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            dto.setAttachmentFile(file);
        }
        return dto;
    }

    @Test
    void create_WithAttachment_Uploads_Attaches_ValidatesProducts_ComputesTotal_AndSaves() throws Exception {
        // given
        PurchaseOrderDTO dto = buildDtoWithItems(true);

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(uploadService.uploadImage(any())).thenReturn("https://cdn/po-attach.png");
        when(productRepo.findAllById(List.of(10, 11))).thenReturn(List.of(p1, p2));
        when(orderRepo.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        PurchaseOrder po = service.create(dto);

        // then
        assertNotNull(po.getCode(), "Phải sinh mã PO");
        assertEquals(PurchaseOrderStatus.CHO_GIAO_HANG, po.getStatus(), "Trạng thái khởi tạo phải CHO_GIAO_HANG");
        assertEquals(2, po.getItems().size(), "Phải sinh 2 dòng hàng");
        // total = 3*2.50 + 5*1.20 = 7.50 + 6.00 = 13.50
        assertEquals(0, po.getTotalAmount().compareTo(new BigDecimal("13.50")));
        assertEquals("admin", po.getCreatedByName());

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderRepo, atLeastOnce()).save(cap.capture());
        PurchaseOrder saved = cap.getValue();
        assertEquals(po.getCode(), saved.getCode());
        verify(uploadService, atLeastOnce()).uploadImage(any());
    }

    @Test
    void create_ProductNotBelongToSupplier_ThrowsInventoryException() {
        // given: p2 không thuộc NCC
        Product wrong = new Product();
        wrong.setId(11);
        wrong.setName("P2");
        wrong.setSuppliers(Set.of()); // không có NCC

        PurchaseOrderDTO dto = buildDtoWithItems(false);

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findAllById(List.of(10, 11))).thenReturn(List.of(p1, wrong));

        // then
        InventoryException ex = assertThrows(InventoryException.class, () -> service.create(dto));
        assertTrue(ex.getMessage().contains("sản phẩm không thuộc nhà cung cấp"), "Thông báo phải nêu sai NCC");
        verify(orderRepo, never()).save(any());
    }

    @Test
    void updateStatus_DeliveredPartially_IncreasesStockAndSaves() throws Exception {
        // order với 2 dòng, có actualQuantity
        PurchaseOrder order = new PurchaseOrder();
        order.setId(100L);
        order.setStatus(PurchaseOrderStatus.CHO_GIAO_HANG);

        PurchaseOrderItem it1 = PurchaseOrderItem.builder().product(p1).actualQuantity(3).price(new BigDecimal("1.0")).contractQuantity(3).build();
        PurchaseOrderItem it2 = PurchaseOrderItem.builder().product(p2).actualQuantity(2).price(new BigDecimal("1.0")).contractQuantity(2).build();
        order.setItems(List.of(it1, it2));

        when(orderRepo.findById(100L)).thenReturn(Optional.of(order));

        // when
        service.updateStatus(100L, PurchaseOrderStatus.DA_GIAO_MOT_PHAN, null);

        // then: tăng tồn
        assertEquals(3, p1.getQuantity());
        assertEquals(2, p2.getQuantity());
        assertEquals(PurchaseOrderStatus.DA_GIAO_MOT_PHAN, order.getStatus());
        verify(productRepo, times(2)).save(any(Product.class));
        verify(orderRepo, atLeastOnce()).save(order);
    }

    @Test
    void updateStatus_Completed_IncreasesStockAndSaves() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(101L);
        order.setStatus(PurchaseOrderStatus.CHO_GIAO_HANG);

        PurchaseOrderItem it1 = PurchaseOrderItem.builder().product(p1).actualQuantity(1).price(new BigDecimal("1.0")).contractQuantity(1).build();
        order.setItems(List.of(it1));

        when(orderRepo.findById(101L)).thenReturn(Optional.of(order));

        service.updateStatus(101L, PurchaseOrderStatus.HOAN_THANH, null);

        assertEquals(1, p1.getQuantity());
        assertEquals(PurchaseOrderStatus.HOAN_THANH, order.getStatus());
        verify(productRepo, times(1)).save(any(Product.class));
        verify(orderRepo).save(order);
    }

    @Test
    void updateStatus_OrderNotFound_Throws() {
        when(orderRepo.findById(404L)).thenReturn(Optional.empty());
        InventoryException ex = assertThrows(InventoryException.class,
                () -> service.updateStatus(404L, PurchaseOrderStatus.HOAN_THANH, null));
        assertTrue(ex.getMessage().toLowerCase().contains("không tìm thấy"));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void generateOrderCode_NextIndexIsMaxPlusOne() {
        PurchaseOrder a = new PurchaseOrder(); a.setCode("P00009");
        PurchaseOrder b = new PurchaseOrder(); b.setCode("P00015");
        when(orderRepo.findAll()).thenReturn(List.of(a, b));

        String code = service.generateOrderCode();
        assertEquals("P00016", code);
    }

    @Test
    void findAll_MapsToDTO() {
        PurchaseOrder a = new PurchaseOrder();
        a.setCode("P00001"); a.setStatus(PurchaseOrderStatus.CHO_GIAO_HANG);
        when(orderRepo.findAll()).thenReturn(List.of(a));

        List<PurchaseOrderDTO> list = service.findAll();
        assertEquals(1, list.size());
        assertEquals("P00001", list.get(0).getCode());
    }

    @Test
    void searchWithFilters_DelegatesToRepo() {
        PurchaseOrder a = new PurchaseOrder(); a.setCode("P00002");
        Page<PurchaseOrder> page = new PageImpl<>(List.of(a));
        when(orderRepo.searchWithFilters(eq("po"), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<PurchaseOrderDTO> res = service.searchWithFilters("po", null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), Pageable.unpaged());
        assertEquals(1, res.getTotalElements());
        verify(orderRepo).searchWithFilters(any(), any(), any(), any(), any(Pageable.class));
    }
}
