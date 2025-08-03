package com.eewms.services.impl;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderMapper;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.*;
import com.eewms.services.IPurchaseOrderService;
import com.eewms.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements IPurchaseOrderService {

    private final PurchaseOrderRepository orderRepo;
    private final PurchaseOrderItemRepository itemRepo;
    private final SupplierRepository supplierRepo;
    private final ProductRepository productRepo;
    private final ImageUploadService uploadService;

    @Override
    @Transactional
    public PurchaseOrder create(PurchaseOrderDTO dto) throws Exception {
        Supplier supplier = supplierRepo.findById(dto.getSupplierId())
                .orElseThrow(() -> new InventoryException("Nhà cung cấp không tồn tại"));

        String attachmentUrl = null;
        if (dto.getAttachmentFile() != null && !dto.getAttachmentFile().isEmpty()) {
            attachmentUrl = uploadService.uploadImage(dto.getAttachmentFile());
        }

        // Tạo đơn hàng
        PurchaseOrder order = PurchaseOrderMapper.toEntity(dto, supplier, attachmentUrl);
        order.setCreatedByName(dto.getCreatedByName());
        order.setCode(generateOrderCode());
        order.setStatus(PurchaseOrderStatus.CHO_GIAO_HANG);

        // Tính tổng tiền
        List<Product> products = productRepo.findAllById(
                dto.getItems().stream().map(i -> i.getProductId()).toList()
        );
        List<PurchaseOrderItem> items = PurchaseOrderMapper.toItemEntities(dto.getItems(), order, products);

        BigDecimal total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getContractQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);

        // Gán items → lưu
        order.setItems(items);
        return orderRepo.save(order); // Cascade ALL sẽ tự lưu items
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderDTO> findAll() {
        return orderRepo.findAll().stream()
                .map(PurchaseOrderMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseOrder> findById(Long id) {
        return orderRepo.findById(id);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, PurchaseOrderStatus status, PurchaseOrderDTO dto) throws Exception {
        PurchaseOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        // Nếu trạng thái mới là giao 1 phần hoặc hoàn thành → cộng tồn kho
        if (status == PurchaseOrderStatus.DA_GIAO_MOT_PHAN || status == PurchaseOrderStatus.HOAN_THANH) {
            for (PurchaseOrderItem item : order.getItems()) {
                Integer actual = item.getActualQuantity();
                if (actual != null && actual > 0) {
                    Product product = item.getProduct();
                    product.setQuantity(product.getQuantity() + actual);
                    productRepo.save(product);
                }
            }
        }

        // Cập nhật trạng thái
        order.setStatus(status);
        orderRepo.save(order);
    }

    @Override
    public String generateOrderCode() {
        List<PurchaseOrder> all = orderRepo.findAll();
        long nextNumber = all.stream()
                .map(PurchaseOrder::getCode)
                .filter(code -> code != null && code.matches("P\\d+"))
                .map(code -> Long.parseLong(code.replace("P", "")))
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;

        return String.format("P%05d", nextNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrderDTO> searchWithFilters(String keyword, PurchaseOrderStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return orderRepo.searchWithFilters(keyword, status, from, to, pageable)
                .map(PurchaseOrderMapper::toDTO);
    }
}
