package com.eewms.services;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.repository.warehouseReceipt.ProductWarehouseStockRepository; // 👈 THÊM
import com.eewms.services.impl.WarehouseReceiptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseReceiptServiceImplTest {

    @InjectMocks
    private WarehouseReceiptServiceImpl service;

    @Mock private WarehouseReceiptRepository receiptRepository;
    @Mock private WarehouseReceiptItemRepository receiptItemRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductWarehouseStockRepository productWarehouseStockRepository; // 👈 THÊM

    private Product p1, p2;
    private PurchaseOrder po;
    private User admin;
    private Warehouse warehouse;

    private ProductWarehouseStock s1, s2; // 👈 stock theo kho

    @BeforeEach
    void setUp() {
        admin = new User(); admin.setUsername("admin");

        warehouse = new Warehouse();
        warehouse.setId(1L); warehouse.setName("Main WH");

        p1 = new Product(); p1.setId(1); p1.setName("A"); p1.setQuantity(0);
        p2 = new Product(); p2.setId(2); p2.setName("B"); p2.setQuantity(0);

        // PO có contract qty 3 & 2
        PurchaseOrderItem poi1 = PurchaseOrderItem.builder()
                .product(p1).contractQuantity(3).price(new BigDecimal("1.0")).build();
        PurchaseOrderItem poi2 = PurchaseOrderItem.builder()
                .product(p2).contractQuantity(2).price(new BigDecimal("1.0")).build();

        po = new PurchaseOrder();
        po.setId(100L);
        po.setCode("P00001");
        po.setStatus(PurchaseOrderStatus.CHO_GIAO_HANG);
        po.setItems(List.of(poi1, poi2));

        // Tạo bản ghi tồn kho theo kho (ban đầu = 0)
        s1 = new ProductWarehouseStock(); s1.setProduct(p1); s1.setWarehouse(warehouse); s1.setQuantity(0);
        s2 = new ProductWarehouseStock(); s2.setProduct(p2); s2.setWarehouse(warehouse); s2.setQuantity(0);
    }

    private WarehouseReceiptDTO dto(int q1, int q2) {
        WarehouseReceiptItemDTO r1 = WarehouseReceiptItemDTO.builder()
                .productId(1L).actualQuantity(q1).price(new BigDecimal("1.0")).build();
        WarehouseReceiptItemDTO r2 = WarehouseReceiptItemDTO.builder()
                .productId(2L).actualQuantity(q2).price(new BigDecimal("1.0")).build();
        WarehouseReceiptDTO dto = new WarehouseReceiptDTO();
        dto.setWarehouseId(1L);              // service sẽ dùng để load kho
        dto.setItems(List.of(r1, r2));
        return dto;
    }

    /** Stub dùng chung cho các test thành công */
    private void commonStubs() {
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));


        when(productWarehouseStockRepository.findByProductAndWarehouse(p1, warehouse)).thenReturn(Optional.ofNullable(s1));
        when(productWarehouseStockRepository.findByProductAndWarehouse(p2, warehouse)).thenReturn(Optional.ofNullable(s2));


        // 👇 Quan trọng: stub kho–sản phẩm để tránh NPE
        when(productWarehouseStockRepository.findByProductAndWarehouse(p1, warehouse))
                .thenReturn(Optional.of(s1)); // nếu method không trả Optional, dùng thenReturn(s1)
        when(productWarehouseStockRepository.findByProductAndWarehouse(p2, warehouse))
                .thenReturn(Optional.of(s2));
        when(productWarehouseStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void saveReceipt_FirstTime_Partial_IncreaseStock_UpdatePO_SaveAll() {
        when(receiptRepository.existsByPurchaseOrder(po)).thenReturn(false);
        commonStubs();

        WarehouseReceiptDTO d = dto(2, 1); // nhận 2 & 1 -> giao một phần

        service.saveReceipt(d, po, admin);

        assertEquals(2, p1.getQuantity());
        assertEquals(1, p2.getQuantity());
        assertEquals(PurchaseOrderStatus.DA_GIAO_MOT_PHAN, po.getStatus());

        // stock theo kho cũng được cập nhật
        assertEquals(2, s1.getQuantity());
        assertEquals(1, s2.getQuantity());

        verify(productRepository, times(2)).save(any(Product.class));
        verify(productWarehouseStockRepository, times(2)).save(any(ProductWarehouseStock.class));
        verify(receiptRepository, atLeastOnce()).save(any());
        verify(receiptItemRepository, atLeast(2)).save(any());
    }

    @Test
    void saveReceipt_EnoughForCompletion_UpdatePOToCompleted() {
        when(receiptRepository.existsByPurchaseOrder(po)).thenReturn(false);
        commonStubs();

        WarehouseReceiptDTO d = dto(3, 2); // nhận đủ

        service.saveReceipt(d, po, admin);

        assertEquals(3, p1.getQuantity());
        assertEquals(2, p2.getQuantity());
        assertEquals(PurchaseOrderStatus.HOAN_THANH, po.getStatus());

        assertEquals(3, s1.getQuantity());
        assertEquals(2, s2.getQuantity());

        verify(productRepository, times(2)).save(any(Product.class));
        verify(productWarehouseStockRepository, times(2)).save(any(ProductWarehouseStock.class));
    }

    @Test
    void saveReceipt_DuplicateByPO_Throws_NoSave() {
        when(receiptRepository.existsByPurchaseOrder(po)).thenReturn(true);
        // Dù duplicate, mock warehouse để tránh NPE nếu service chạm tới:
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

        WarehouseReceiptDTO d = dto(1, 1);

        InventoryException ex = assertThrows(InventoryException.class,
                () -> service.saveReceipt(d, po, admin));
        assertTrue(ex.getMessage().toLowerCase().contains("tồn tại") ||
                ex.getMessage().toLowerCase().contains("đã có"));

        verify(receiptRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        verify(productWarehouseStockRepository, never()).save(any());
    }
}
