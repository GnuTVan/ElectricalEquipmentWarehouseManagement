package com.eewms.services.impl;

import com.eewms.constant.InventoryCountStatus;
import com.eewms.dto.inventory.InventoryCountDTO;
import com.eewms.entities.*;
import com.eewms.dto.inventory.InventoryCountMapper;
import com.eewms.repository.*;
import com.eewms.services.IInventoryCountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public  class InventoryCountServiceImpl implements IInventoryCountService {

    private final InventoryCountRepository countRepo;
    private final InventoryCountItemRepository itemRepo;
    private final UserRepository userRepo;
    private final WarehouseStaffRepository warehouseStaffRepo;
    private final ProductWarehouseStockRepository stockRepo;

    @Override
    @Transactional
    public InventoryCountDTO create(Integer staffId, String note) {
        User staff = userRepo.findById(Long.valueOf(staffId))
                .orElseThrow(() -> new RuntimeException("Staff không tồn tại"));

        // lấy warehouse mà staff thuộc về
        WarehouseStaff ws = warehouseStaffRepo.findByUser_Id(Long.valueOf(staffId))
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Staff chưa được assign vào kho"));

        Warehouse warehouse = ws.getWarehouse();

        InventoryCount count = InventoryCount.builder()
                .code(generateCode())
                .warehouse(warehouse)
                .assignedStaff(staff)
                .status(InventoryCountStatus.IN_PROGRESS)
                .note(note)
                .build();

        // sinh danh sách item từ stock
        List<InventoryCountItem> items = stockRepo.findByWarehouse(warehouse).stream()
                .map(stock -> InventoryCountItem.builder()
                        .inventoryCount(count)
                        .product(stock.getProduct())
                        .expectedQty(stock.getQuantity() != null ? stock.getQuantity().intValue() : 0)
                        .countedQty(null)
                        .variance(0)
                        .note(null)
                        .build())
                .toList();

        count.setItems(items);

        InventoryCount saved = countRepo.save(count);
        return InventoryCountMapper.toDTO(saved);
    }

    @Override
    public InventoryCountDTO getById(Integer id) {
        InventoryCount count = countRepo.findWithDetailsById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu kiểm kê"));
        return InventoryCountMapper.toDTO(count);
    }

    @Override
    public List<InventoryCountDTO> getAll() {
        return countRepo.findAll().stream()
                .map(InventoryCountMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public InventoryCountDTO submit(Integer id, List<Integer> countedQtys, List<String> notes) {
        InventoryCount count = countRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Phiếu không tồn tại"));

        List<InventoryCountItem> items = count.getItems();
        if (items.size() != countedQtys.size()) {
            throw new RuntimeException("Dữ liệu submit không khớp với số item");
        }

        for (int i = 0; i < items.size(); i++) {
            InventoryCountItem item = items.get(i);
            Integer counted = countedQtys.get(i);
            if (counted == null) {
                throw new RuntimeException("Thiếu số lượng thực đếm cho sản phẩm " + item.getProduct().getName());
            }
            item.setCountedQty(counted);
            item.setVariance(counted - item.getExpectedQty());
            item.setNote(notes != null ? notes.get(i) : null);
        }

        count.setStatus(InventoryCountStatus.SUBMITTED);
        countRepo.save(count);
        return InventoryCountMapper.toDTO(count);
    }

    @Override
    @Transactional
    public InventoryCountDTO saveDraft(Integer id, List<Integer> countedQtys, List<String> notes) {
        InventoryCount count = countRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Phiếu không tồn tại"));

        List<InventoryCountItem> items = count.getItems();
        if (items.size() != countedQtys.size()) {
            throw new RuntimeException("Dữ liệu draft không khớp với số item");
        }

        for (int i = 0; i < items.size(); i++) {
            InventoryCountItem item = items.get(i);
            Integer counted = countedQtys.get(i);
            item.setCountedQty(counted);
            item.setVariance(counted != null ? counted - item.getExpectedQty() : 0);
            item.setNote(notes != null ? notes.get(i) : null);
        }

        // giữ nguyên status = IN_PROGRESS
        countRepo.save(count);
        return InventoryCountMapper.toDTO(count);
    }
    @Override
    @Transactional
    public InventoryCountDTO approve(Integer id, String approveNote) {
        InventoryCount count = countRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Phiếu không tồn tại"));

        if (count.getStatus() != InventoryCountStatus.SUBMITTED) {
            throw new RuntimeException("Chỉ duyệt phiếu ở trạng thái REVIEW");
        }

        count.setStatus(InventoryCountStatus.APPROVED);
        count.setNote(approveNote);
        countRepo.save(count);
        return InventoryCountMapper.toDTO(count);
    }
    @Override
    @Transactional
    public InventoryCountDTO reopen(Integer id, String approveNote) {
        InventoryCount count = countRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Phiếu không tồn tại"));

        if (count.getStatus() != InventoryCountStatus.SUBMITTED) {
            throw new RuntimeException("Chỉ có thể mở lại phiếu ở trạng thái SUBMITTED");
        }

        count.setStatus(InventoryCountStatus.IN_PROGRESS); // staff nhập lại
        count.setNote(approveNote);
        countRepo.save(count);

        return InventoryCountMapper.toDTO(count);
    }
    @Override
    public List<InventoryCountDTO> getByStaffId(Long staffId) {
        return countRepo.findByAssignedStaff_Id(staffId).stream()
                .map(InventoryCountMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        countRepo.deleteById(id);
    }

    private String generateCode() {
        return "INV-" + System.currentTimeMillis();
    }
}
