package com.eewms.services.impl;

import com.eewms.entities.*;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.inventoryTransfer.InventoryTransferItemRepository;
import com.eewms.repository.inventoryTransfer.InventoryTransferRepository;
import com.eewms.repository.ProductWarehouseStockRepository;
import com.eewms.repository.UserRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IInventoryTransferService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventoryTransferServiceImpl implements IInventoryTransferService {

    // ========= Dependencies =========
    private final InventoryTransferRepository transferRepo;
    private final InventoryTransferItemRepository itemRepo;
    private final ProductWarehouseStockRepository stockRepo;

    private final ProductRepository productRepository;
    private final UserRepository userRepo;
    private final WarehouseRepository whRepo;

    // ========= Search / Get =========
    @Override
    public Page<InventoryTransfer> search(String keyword,
                                          InventoryTransfer.Status status,
                                          LocalDateTime fromDate,
                                          LocalDateTime toDate,
                                          Integer fromWarehouseId,
                                          Integer toWarehouseId,
                                          Pageable pageable) {
        return transferRepo.search(keyword, status, fromDate, toDate, fromWarehouseId, toWarehouseId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryTransfer get(Long id) {
        return findTransferOrThrow(id);
    }

    // ========= Create Draft =========
    @Override
    @Transactional
    public InventoryTransfer createDraft(InventoryTransfer draft) {
        // 1) Validate cơ bản
        validateDraftHeader(draft);
        validateDraftItems(draft);

        // 2) Attach warehouse managed entities
        draft.setFromWarehouse(findWarehouseOrThrow(draft.getFromWarehouse().getId()));
        draft.setToWarehouse(findWarehouseOrThrow(draft.getToWarehouse().getId()));

        // 3) Sinh code + set trạng thái
        draft.setCode(generateUniqueCode());
        draft.setStatus(InventoryTransfer.Status.DRAFT);

        // 4) Snapshot unitName & set back-reference
        snapshotItemUnitsAndBind(draft);

        // 5) Lưu (cascade sẽ lưu items)
        return transferRepo.save(draft);
    }

    // ========= Approvals =========

    /**
     * Duyệt phía KHO ĐÍCH (TO) — chỉ cho phép khi đã DRAFT
     */
    @Override
    @Transactional
    public InventoryTransfer approveTo(Long id, Integer managerUserId) {
        InventoryTransfer tr = findTransferOrThrow(id);
        requireStatus(tr, InventoryTransfer.Status.DRAFT);
        tr.setToApprovedBy(findUserOrThrow(managerUserId));
        tr.setToApprovedAt(now());
        tr.setStatus(InventoryTransfer.Status.TO_APPROVED);
        return tr;
    }

    /**
     * Duyệt phía KHO NGUỒN (FROM) — chỉ cho phép khi đã TO_APPROVE
     */
    @Override
    @Transactional
    public InventoryTransfer approveFrom(Long id, Integer managerUserId) {
        InventoryTransfer tr = findTransferOrThrow(id);
        requireStatus(tr, InventoryTransfer.Status.TO_APPROVED);
        tr.setFromApprovedBy(findUserOrThrow(managerUserId));
        tr.setFromApprovedAt(now());
        tr.setStatus(InventoryTransfer.Status.FROM_APPROVED);
        return tr;
    }

    // ========= Export / Import =========

    /**
     * Xuất kho nguồn (trừ tồn) — chỉ khi đã FROM_APPROVED
     */
    @Override
    @Transactional
    public InventoryTransfer export(Long id, Integer staffUserId) {
        InventoryTransfer tr = findTransferOrThrow(id);
        requireStatus(tr, InventoryTransfer.Status.FROM_APPROVED);

        Integer fromWhId = tr.getFromWarehouse().getId();
        // 1) Kiểm tra đủ tồn cho tất cả dòng (có lock)
        for (InventoryTransferItem it : tr.getItems()) {
            var pws = lockStockOrThrow(it.getProduct().getId(), fromWhId,
                    "Kho nguồn chưa có tồn cho sản phẩm " + safeProductCode(it));
            if (lt(pws.getQuantity(), it.getQuantity())) {
                throw new IllegalStateException("Tồn kho nguồn không đủ cho sản phẩm " + safeProductCode(it));
            }
        }
        // 2) Trừ tồn (đã khoá)
        for (InventoryTransferItem it : tr.getItems()) {
            var pws = lockStockOrThrow(it.getProduct().getId(), fromWhId, "Không tìm thấy tồn kho khi trừ: " + safeProductCode(it));
            pws.setQuantity(pws.getQuantity().subtract(it.getQuantity()));
            // JPA managed, không cần save trừ khi detach
        }

        tr.setExportedBy(findUserOrThrow(staffUserId));
        tr.setExportedAt(now());
        tr.setStatus(InventoryTransfer.Status.EXPORTED);
        return tr;
    }

    /**
     * Nhập kho đích (cộng tồn) — chỉ khi đã EXPORTED
     */
    @Override
    @Transactional
    public InventoryTransfer importTo(Long id, Integer staffUserId) {
        InventoryTransfer tr = findTransferOrThrow(id);
        requireStatus(tr, InventoryTransfer.Status.EXPORTED);

        Integer toWhId = tr.getToWarehouse().getId();

        // Cộng tồn (lock theo từng (product, toWh))
        for (InventoryTransferItem it : tr.getItems()) {
            var pws = stockRepo.findForUpdate(it.getProduct().getId(), toWhId)
                    .orElseGet(() -> newPws(it.getProduct(), tr.getToWarehouse()));
            pws.setQuantity(pws.getQuantity().add(it.getQuantity()));
            stockRepo.save(pws); // cần save khi create mới
        }

        tr.setImportedBy(findUserOrThrow(staffUserId));
        tr.setImportedAt(now());
        tr.setStatus(InventoryTransfer.Status.IMPORTED);
        return tr;
    }

    // ========= Cancel =========
    @Override
    @Transactional
    public void cancel(Long id, Integer byUserId) {
        InventoryTransfer tr = findTransferOrThrow(id);
        requireStatus(tr, InventoryTransfer.Status.DRAFT);
        tr.setStatus(InventoryTransfer.Status.CANCELED);
    }

    // ========= Helpers (đọc dễ, tách nhỏ) =========

    private void validateDraftHeader(InventoryTransfer draft) {
        if (draft.getFromWarehouse() == null || draft.getFromWarehouse().getId() == null)
            throw new IllegalArgumentException("Vui lòng chọn kho nguồn");
        if (draft.getToWarehouse() == null || draft.getToWarehouse().getId() == null)
            throw new IllegalArgumentException("Vui lòng chọn kho đích");
        if (draft.getFromWarehouse().getId().equals(draft.getToWarehouse().getId()))
            throw new IllegalArgumentException("Kho nguồn và kho đích phải khác nhau");
        if (draft.getCreatedBy() == null || draft.getCreatedBy().getId() == null)
            throw new IllegalArgumentException("Thiếu thông tin người tạo phiếu");
    }

    private void validateDraftItems(InventoryTransfer draft) {
        if (draft.getItems() == null || draft.getItems().isEmpty())
            throw new IllegalArgumentException("Phiếu phải có ít nhất 1 dòng hàng");
        for (InventoryTransferItem it : draft.getItems()) {
            if (it.getProduct() == null || it.getProduct().getId() == null)
                throw new IllegalArgumentException("Thiếu sản phẩm ở một dòng");
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("Số lượng phải > 0");
        }
    }

    /**
     * Sao chép tên đơn vị từ Product.unit vào unitName và bind ngược transfer
     */
    private void snapshotItemUnitsAndBind(InventoryTransfer transfer) {
        for (InventoryTransferItem it : transfer.getItems()) {
            // --- 1) Kiểm tra & nạp lại Product kèm Unit để tránh lazy/detached ---
            if (it.getProduct() == null || it.getProduct().getId() == null) {
                throw new IllegalArgumentException("Thiếu sản phẩm ở một dòng.");
            }
            var p = productRepository.findWithUnit(it.getProduct().getId().intValue())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy sản phẩm id=" + it.getProduct().getId()));
            it.setProduct(p); // dùng entity đã được quản lý bởi JPA

            // --- 2) Snapshot đơn vị đo (ưu tiên từ Product.unit, fallback giá trị form nếu có) ---
            String unitName = (p.getUnit() != null) ? p.getUnit().getName() : it.getUnitName();
            if (unitName == null || unitName.isBlank()) {
                throw new IllegalArgumentException("Sản phẩm thiếu đơn vị đo: " + p.getName());
            }
            it.setUnitName(unitName);

            // --- 3) QUAN TRỌNG: bind ngược item -> transfer để Hibernate set transfer_id ---
            it.setTransfer(transfer);
        }
    }

    private ProductWarehouseStock lockStockOrThrow(Integer productId, Integer warehouseId, String errMsg) {
        return stockRepo.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> new IllegalStateException(errMsg));
    }

    private ProductWarehouseStock newPws(Product product, Warehouse wh) {
        ProductWarehouseStock p = new ProductWarehouseStock();
        p.setProduct(product);
        p.setWarehouse(wh);
        p.setQuantity(BigDecimal.ZERO);
        return p;
    }

    private void requireStatus(InventoryTransfer tr, InventoryTransfer.Status expected) {
        if (tr.getStatus() != expected) {
            throw new IllegalStateException("Trạng thái không hợp lệ. Cần: " + expected.name() + ", hiện tại: " + tr.getStatus().name());
        }
    }

    private InventoryTransfer findTransferOrThrow(Long id) {
        return transferRepo.findDetailById(id).orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phiếu chuyển kho"));
    }

    private Warehouse findWarehouseOrThrow(Integer id) {
        return whRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Kho không tồn tại"));
    }

    private User findUserOrThrow(Integer id) {
        return userRepo.findById(Long.valueOf(id)).orElseThrow(() -> new EntityNotFoundException("User không tồn tại"));
    }

    private String generateUniqueCode() {
        String prefix = "CK" + LocalDate.now().toString().replace("-", "");
        for (int seq = 1; seq <= 9999; seq++) {
            String code = prefix + "-" + String.format("%04d", seq);
            if (!transferRepo.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Không sinh được mã phiếu (quá 9999 trong ngày)");
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private boolean lt(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) < 0;
    }

    private String safeProductCode(InventoryTransferItem it) {
        try {
            return it.getProduct().getCode();
        } catch (Exception e) {
            return "(unknown)";
        }
    }
}
