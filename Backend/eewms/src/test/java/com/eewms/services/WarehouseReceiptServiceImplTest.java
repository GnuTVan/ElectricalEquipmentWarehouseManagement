package com.eewms.services;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.dto.warehouseReceipt.WarehouseReceiptItemDTO;
import com.eewms.entities.*;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.warehouseReceipt.ProductWarehouseStockRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.impl.WarehouseReceiptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
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
    @Mock private ProductWarehouseStockRepository productWarehouseStockRepository;

    private Product p1, p2;
    private PurchaseOrder po;
    private User admin;
    private Warehouse wh;
    private ProductWarehouseStock s1, s2;

    @BeforeEach
    void setUp() {
        admin = new User(); admin.setUsername("admin");

        wh = new Warehouse(); wh.setId(1L); wh.setName("Main");

        p1 = new Product(); p1.setId(1); p1.setName("A"); p1.setQuantity(0);
        p2 = new Product(); p2.setId(2); p2.setName("B"); p2.setQuantity(0);

        PurchaseOrderItem poi1 = PurchaseOrderItem.builder()
                .product(p1).contractQuantity(3).price(new BigDecimal("1.0")).build();
        PurchaseOrderItem poi2 = PurchaseOrderItem.builder()
                .product(p2).contractQuantity(2).price(new BigDecimal("1.0")).build();

        po = new PurchaseOrder();
        po.setId(100L);
        po.setCode("PO0001");
        po.setStatus(PurchaseOrderStatus.CHO_GIAO_HANG);
        po.setItems(List.of(poi1, poi2));

        s1 = new ProductWarehouseStock(); s1.setProduct(p1); s1.setWarehouse(wh); s1.setQuantity(0);
        s2 = new ProductWarehouseStock(); s2.setProduct(p2); s2.setWarehouse(wh); s2.setQuantity(0);
    }

    private static void setWarehouseOnDto(WarehouseReceiptDTO dto, Long wid) {
        tryCall(dto, "setWarehouseId", Long.class, wid);
        tryCall(dto, "setWarehouse",   Long.class, wid);
        if (wid != null) {
            tryCall(dto, "setWarehouseId", Integer.class, wid.intValue());
            tryCall(dto, "setWarehouse",   Integer.class, wid.intValue());
        }
    }
    private static void tryCall(Object target, String method, Class<?> p, Object arg) {
        try {
            Method m = target.getClass().getMethod(method, p);
            m.invoke(target, arg);
        } catch (Exception ignore) {}
    }

    private WarehouseReceiptDTO dto(int q1, int q2) {
        WarehouseReceiptItemDTO r1 = WarehouseReceiptItemDTO.builder()
                .productId(1).actualQuantity(q1).price(new BigDecimal("1.0")).build();
        WarehouseReceiptItemDTO r2 = WarehouseReceiptItemDTO.builder()
                .productId(2).actualQuantity(q2).price(new BigDecimal("1.0")).build();
        WarehouseReceiptDTO d = new WarehouseReceiptDTO();
        setWarehouseOnDto(d, 1L);            // << không gọi trực tiếp setter, tránh lỗi “không tồn tại”
        d.setItems(List.of(r1, r2));
        return d;
    }

    private void commonStubs() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(wh));
        when(productWarehouseStockRepository.findByProductAndWarehouse(p1, wh)).thenReturn(Optional.of(s1));
        when(productWarehouseStockRepository.findByProductAndWarehouse(p2, wh)).thenReturn(Optional.of(s2));

        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productWarehouseStockRepository.save(any(ProductWarehouseStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    }

    @Test
    void saveReceipt_FirstTime_Partial_IncreaseStock_UpdatePO_SaveAll() {
        commonStubs();

        WarehouseReceiptDTO d = dto(2, 1); // nhận 2 & 1 -> giao một phần

        service.saveReceipt(d, po, admin);

        assertEquals(2, p1.getQuantity());
        assertEquals(1, p2.getQuantity());
        assertEquals(PurchaseOrderStatus.DA_GIAO_MOT_PHAN, po.getStatus());

        assertEquals(2, s1.getQuantity());
        assertEquals(1, s2.getQuantity());

        verify(productRepository, times(2)).save(any(Product.class));
        verify(productWarehouseStockRepository, times(2)).save(any(ProductWarehouseStock.class));
        verify(receiptRepository, atLeastOnce()).save(any());
        verify(receiptItemRepository, atLeast(2)).save(any());
    }

    @Test
    void saveReceipt_EnoughForCompletion_UpdatePOToCompleted() {
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

}
